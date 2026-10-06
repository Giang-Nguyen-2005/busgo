package com.busgo.operations;

import static com.busgo.operations.OperationsDtos.*;
import com.busgo.common.exception.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Operational writes serialize on trip, then sorted employee rows. No commercial state writes. */
@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class OperationsService {
    private final JdbcTemplate db;
    private final OperatorContextService context;
    private final Clock clock;
    private final com.busgo.fleet.FleetMaintenanceGuard maintenance;
    public OperationsService(JdbcTemplate db, OperatorContextService context, Clock clock,
            com.busgo.fleet.FleetMaintenanceGuard maintenance) {
        this.db=db; this.context=context; this.clock=clock;
        this.maintenance=maintenance;
    }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code,message,HttpStatus.CONFLICT,null);
    }
    private static ResourceNotFoundException missing(String code) {
        return new ResourceNotFoundException(code,"Resource was not found.");
    }
    private Map<String,Object> trip(CurrentUser actor, long id, boolean write) {
        long operator=write ? context.requireAdminOperator(actor).getId() : context.requireOperatorMember(actor).getId();
        var rows=db.queryForList("SELECT t.* FROM trips t JOIN operator_routes r ON r.id=t.operator_route_id WHERE t.id=? AND r.operator_id=?"+(write?" FOR UPDATE":""),id,operator);
        if(rows.isEmpty()) throw missing("TRIP_NOT_FOUND");
        return rows.get(0);
    }
    public List<Employee> employees(CurrentUser actor) {
        long operator=context.requireOperatorMember(actor).getId();
        return db.queryForList("SELECT id FROM operator_employees WHERE operator_id=? ORDER BY employee_code,id",Long.class,operator)
                .stream().map(id->employeeOwned(operator,id,false)).toList();
    }
    public Employee employee(CurrentUser actor,long id) { return employeeOwned(context.requireOperatorMember(actor).getId(),id,false); }
    private Employee employeeOwned(long operator,long id,boolean lock) {
        var rows=db.queryForList("SELECT * FROM operator_employees WHERE operator_id=? AND id=?"+(lock?" FOR UPDATE":""),operator,id);
        if(rows.isEmpty()) throw missing("EMPLOYEE_NOT_FOUND");
        var e=rows.get(0);
        var caps=new HashSet<Capability>();
        db.queryForList("SELECT capability FROM employee_capabilities WHERE employee_id=?",String.class,id).forEach(c->caps.add(Capability.valueOf(c)));
        var profiles=db.queryForList("SELECT * FROM driver_profiles WHERE employee_id=?",id);
        var p=profiles.isEmpty()?Map.<String,Object>of():profiles.get(0);
        return new Employee(id,(String)e.get("employee_code"),(String)e.get("full_name"),(String)e.get("phone"),EmployeeStatus.valueOf((String)e.get("status")),caps,
                (String)p.get("licence_number"),(String)p.get("licence_class"),p.get("licence_expiry_date")==null?null:((java.sql.Date)p.get("licence_expiry_date")).toLocalDate(),((Number)e.get("version")).longValue());
    }
    public Employee saveEmployee(CurrentUser actor,Long id,EmployeeInput input) {
        long operator=context.requireAdminOperator(actor).getId();
        db.queryForObject("SELECT id FROM transport_operators WHERE id=? FOR UPDATE",Long.class,operator);
        if(input.capabilities().contains(Capability.DRIVER) && (input.licenceExpiryDate()==null || input.licenceNumber()==null || input.licenceNumber().isBlank() || input.licenceClass()==null || input.licenceClass().isBlank()))
            throw conflict("DRIVER_PROFILE_REQUIRED","Driver licence fields are required.");
        if(id!=null) {
            Employee old=employeeOwned(operator,id,true);
            if(input.version()==null || input.version()!=old.version()) throw conflict("STALE_EMPLOYEE","Reload employee before editing.");
            var duties=db.queryForList("SELECT a.duty FROM trip_crew_assignments a JOIN trips t ON t.id=a.trip_id WHERE a.employee_id=? AND a.released_at IS NULL AND t.status IN ('SCHEDULED','BOARDING','DEPARTED')",String.class,id);
            if(!duties.isEmpty() && (input.status()==EmployeeStatus.INACTIVE || duties.stream().anyMatch(d->!input.capabilities().contains(Capability.valueOf(d)))))
                throw conflict("EMPLOYEE_HAS_ASSIGNMENTS","Release active trip assignments before deactivation or removing their capability.");
        }
        var duplicates=db.queryForList("SELECT id FROM operator_employees WHERE operator_id=? AND employee_code=?",Long.class,operator,input.employeeCode().strip());
        for (Long existing : duplicates) if (!existing.equals(id)) throw conflict("EMPLOYEE_CODE_EXISTS","Employee code already exists.");
        if(id==null) {
            db.update("INSERT INTO operator_employees(operator_id,employee_code,full_name,phone,status,created_at,updated_at) VALUES(?,?,?,?,?,?,?)",operator,input.employeeCode().strip(),input.fullName().strip(),input.phone().strip(),input.status().name(),now(),now());
            id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        } else db.update("UPDATE operator_employees SET employee_code=?,full_name=?,phone=?,status=?,updated_at=?,version=version+1 WHERE id=?",input.employeeCode().strip(),input.fullName().strip(),input.phone().strip(),input.status().name(),now(),id);
        db.update("DELETE FROM employee_capabilities WHERE employee_id=?",id);
        for(var c:input.capabilities()) db.update("INSERT INTO employee_capabilities VALUES(?,?)",id,c.name());
        // Retain licence history fields when DRIVER is deselected; they do not grant capability.
        if(input.capabilities().contains(Capability.DRIVER)) db.update("INSERT INTO driver_profiles VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE licence_number=VALUES(licence_number),licence_class=VALUES(licence_class),licence_expiry_date=VALUES(licence_expiry_date)",id,input.licenceNumber().strip(),input.licenceClass().strip(),input.licenceExpiryDate());
        return employeeOwned(operator,id,false);
    }
    public Map<String,Object> crew(CurrentUser actor,long tripId) {
        trip(actor,tripId,false);
        var rows=crewRows(tripId);
        String warning=readiness(tripId,false);
        return Map.of("assignments",rows,"ready",warning.isEmpty(),"warning",warning);
    }
    private List<Map<String,Object>> crewRows(long tripId) {
        return db.queryForList("SELECT a.id,a.employee_id AS employeeId,a.duty,e.full_name AS fullName,e.status,p.licence_expiry_date AS licenceExpiryDate FROM trip_crew_assignments a JOIN operator_employees e ON e.id=a.employee_id LEFT JOIN driver_profiles p ON p.employee_id=e.id WHERE a.trip_id=? AND a.released_at IS NULL ORDER BY e.id,a.duty",tripId);
    }
    private boolean overlaps(long employee,long tripId) {
        return db.queryForObject("SELECT COUNT(*) FROM trip_crew_assignments a JOIN trips other ON other.id=a.trip_id JOIN trips target ON target.id=? WHERE a.employee_id=? AND a.released_at IS NULL AND other.id<>target.id AND other.status IN ('SCHEDULED','BOARDING','DEPARTED') AND target.departure_time<other.estimated_arrival_time AND target.estimated_arrival_time>other.departure_time",Long.class,tripId,employee)>0;
    }
    public Map<String,Object> replaceCrew(CurrentUser actor,long tripId,CrewReplacement input) {
        var t=trip(actor,tripId,true);
        if(!Set.of("SCHEDULED","BOARDING").contains(t.get("status"))) throw conflict("CREW_WINDOW_CLOSED","Crew changes require SCHEDULED or BOARDING.");
        db.queryForObject("SELECT id FROM buses WHERE id=? FOR UPDATE",Long.class,t.get("bus_id"));
        long operator=context.requireAdminOperator(actor).getId();
        var current=crewRows(tripId);
        var ids=new TreeSet<Long>(); current.forEach(a->ids.add(((Number)a.get("employeeId")).longValue()));
        input.assignments().forEach(a->ids.add(a.employeeId()));
        var employees=new HashMap<Long,Employee>();
        for(long id:ids) employees.put(id,employeeOwned(operator,id,true));
        var desired=new HashSet<String>();
        for(var a:input.assignments()) {
            if(!desired.add(a.employeeId()+":"+a.duty())) throw conflict("DUPLICATE_CREW","Duplicate assignment.");
            var e=employees.get(a.employeeId());
            if(e.status()!=EmployeeStatus.ACTIVE || !e.capabilities().contains(a.duty())) throw conflict("EMPLOYEE_NOT_ELIGIBLE","Employee must be active and hold the duty capability.");
            if(a.duty()==Capability.DRIVER && !licenceValid(e,t)) throw conflict("DRIVER_LICENCE_EXPIRED","Driver licence must cover trip operation date.");
            if(overlaps(e.id(),tripId)) throw conflict("CREW_SCHEDULE_CONFLICT","Employee is assigned to an overlapping trip.");
        }
        for(var a:current) {
            String key=a.get("employeeId")+":"+a.get("duty");
            if(!desired.remove(key)) {
                long assignment=((Number)a.get("id")).longValue();
                db.update("UPDATE trip_crew_assignments SET released_at=?,released_by=?,version=version+1 WHERE id=?",now(),actor.id(),assignment);
                history(tripId,"CREW",assignment,"CREW_RELEASED",actor.id(),null);
            }
        }
        for(var a:input.assignments()) if(desired.contains(a.employeeId()+":"+a.duty())) {
            db.update("INSERT INTO trip_crew_assignments(trip_id,employee_id,duty,assigned_at,assigned_by) VALUES(?,?,?,?,?)",tripId,a.employeeId(),a.duty().name(),now(),actor.id());
            history(tripId,"CREW",db.queryForObject("SELECT LAST_INSERT_ID()",Long.class),"CREW_ASSIGNED",actor.id(),null);
        }
        if("BOARDING".equals(t.get("status"))) requireReady(tripId);
        return crew(actor,tripId);
    }
    private boolean licenceValid(Employee e,Map<String,Object> t) {
        LocalDate planned=db.queryForObject("SELECT estimated_arrival_time FROM trips WHERE id=?", (rs,n)->com.busgo.common.time.JpaJdbcTime.read(rs,"estimated_arrival_time").atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(), t.get("id"));
        LocalDate actual=clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        return e.licenceExpiryDate()!=null && !e.licenceExpiryDate().isBefore(planned.isAfter(actual)?planned:actual);
    }
    private String readiness(long tripId,boolean lock) {
        if(lock) db.queryForObject("SELECT id FROM buses WHERE id=(SELECT bus_id FROM trips WHERE id=?) FOR UPDATE",Long.class,tripId);
        var t=db.queryForMap("SELECT t.*,r.operator_id,b.status AS bus_status,b.deleted_at AS bus_deleted_at,bt.status AS bus_type_status FROM trips t JOIN operator_routes r ON r.id=t.operator_route_id JOIN buses b ON b.id=t.bus_id JOIN bus_types bt ON bt.id=b.bus_type_id WHERE t.id=?",tripId);
        if(!"AVAILABLE".equals(t.get("bus_status")) || t.get("bus_deleted_at")!=null || !"ACTIVE".equals(t.get("bus_type_status"))) return "Xe chưa sẵn sàng vận hành";
        String maintenanceWarning=db.queryForObject("SELECT bus_id,departure_time,estimated_arrival_time FROM trips WHERE id=?",(rs,n)->maintenance.warning(rs.getLong("bus_id"),com.busgo.common.time.JpaJdbcTime.read(rs,"departure_time"),com.busgo.common.time.JpaJdbcTime.read(rs,"estimated_arrival_time")),tripId);
        if(!maintenanceWarning.isEmpty()) return maintenanceWarning;
        boolean driver=false;
        for(var row:crewRows(tripId)) {
            long id=((Number)row.get("employeeId")).longValue();
            var e=employeeOwned(((Number)t.get("operator_id")).longValue(),id,lock);
            var duty=Capability.valueOf((String)row.get("duty"));
            if(e.status()!=EmployeeStatus.ACTIVE || !e.capabilities().contains(duty) || overlaps(id,tripId)) return "Nhân sự không hợp lệ hoặc trùng lịch";
            if(duty==Capability.DRIVER) { if(!licenceValid(e,t)) return "Giấy phép lái xe hết hạn"; driver=true; }
        }
        return driver?"":"Cần ít nhất một tài xế hợp lệ";
    }
    public void requireReady(long tripId) {
        String warning=readiness(tripId,true);
        if(!warning.isEmpty()) throw conflict("CREW_NOT_READY",warning);
    }
    public boolean collectionOpen(long tripId,long pickupStopId) {
        return db.queryForObject("SELECT COUNT(*) FROM trip_stops s JOIN trips t ON t.id=s.trip_id LEFT JOIN trip_stop_operations o ON o.trip_id=s.trip_id AND o.stop_id=s.id WHERE t.id=? AND s.id=? AND s.allow_pickup=TRUE AND s.status='ACTIVE' AND o.stop_id IS NULL AND (t.status='BOARDING' OR (t.status='DEPARTED' AND s.stop_order>(SELECT MIN(stop_order) FROM trip_stops WHERE trip_id=t.id)))",Long.class,tripId,pickupStopId)>0;
    }
    public void expectTicket(long ticketId,long stopId) {
        db.update("INSERT INTO ticket_boarding(booking_item_id,ticket_id,status,pickup_stop_id) SELECT booking_item_id,id,'EXPECTED',? FROM tickets WHERE id=?",stopId,ticketId);
    }
    /** Called under the payment service's trip lock; a locking read avoids stale RR snapshots. */
    public void requirePaymentAttendanceOpen(long bookingId) {
        if(!db.queryForList("SELECT a.id FROM ticket_boarding a JOIN booking_items bi ON bi.cancelled=FALSE AND bi.id=a.booking_item_id WHERE bi.booking_id=? AND a.status='NO_SHOW' FOR UPDATE",Long.class,bookingId).isEmpty())
            throw conflict("BOOKING_ATTENDANCE_TERMINAL","A no-show reservation cannot receive payment in this milestone.");
    }
    public Map<String,Object> reservationNoShow(CurrentUser actor,long tripId,long itemId,PickupContext input) {
        var t=trip(actor,tripId,true);
        var rows=db.queryForList("SELECT b.id,b.source,b.payment_method,b.status,b.pickup_trip_stop_id,tk.id AS ticket_id FROM booking_items bi JOIN bookings b ON b.id=bi.booking_id JOIN trip_seats s ON s.id=bi.trip_seat_id AND s.trip_id=b.trip_id LEFT JOIN tickets tk ON tk.replaced=FALSE AND tk.booking_item_id=bi.id WHERE bi.cancelled=FALSE AND bi.id=? AND b.trip_id=? FOR UPDATE",itemId,tripId);
        if(rows.isEmpty()) throw missing("BOOKING_ITEM_NOT_FOUND");
        var b=rows.get(0);
        if(((Number)b.get("pickup_trip_stop_id")).longValue()!=input.stopId()) throw conflict("WRONG_PICKUP_STOP","Use the reservation's booked pickup stop.");
        if(b.get("ticket_id")!=null) return transition(actor,tripId,((Number)b.get("ticket_id")).longValue(),"no-show",input);
        if(!"PHONE".equals(b.get("source")) || !"PAY_ON_BOARD".equals(b.get("payment_method")) || !"PENDING".equals(b.get("status")))
            throw conflict("RESERVATION_NOT_ELIGIBLE","Only unpaid PHONE PAY_ON_BOARD reservations can be marked absent without a ticket.");
        var states=db.queryForList("SELECT * FROM ticket_boarding WHERE booking_item_id=? FOR UPDATE",itemId);
        if(!states.isEmpty()) {
            if("NO_SHOW".equals(states.get(0).get("status"))) return states.get(0);
            throw conflict("INVALID_BOARDING_TRANSITION","Terminal attendance cannot change.");
        }
        pickupWindow(t,tripId,input.stopId());
        db.update("INSERT INTO ticket_boarding(booking_item_id,status,pickup_stop_id,no_show_at,no_show_by) VALUES(?,'NO_SHOW',?,?,?)",itemId,input.stopId(),now(),actor.id());
        history(tripId,"BOOKING_ITEM",itemId,"NO_SHOW",actor.id(),input.reason());
        return db.queryForMap("SELECT * FROM ticket_boarding WHERE booking_item_id=?",itemId);
    }
    public List<Map<String,Object>> attendance(CurrentUser actor,long tripId) {
        trip(actor,tripId,false);
        return db.query("""
            SELECT bi.id AS bookingItemId,b.id AS bookingId,b.booking_code AS bookingCode,
              b.total_amount AS bookingAmount,ps.planned_departure_time AS pickupTime,
              bi.seat_code AS seatCode,COALESCE(bi.passenger_name,b.contact_name) AS passengerName,
              b.contact_phone AS phone,b.source AS source,b.payment_method AS paymentMethod,
              EXISTS(SELECT 1 FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.cancelled=FALSE AND i.booking_id=b.id AND a.status='NO_SHOW') AS paymentBlocked,
              COALESCE(p.status,'PENDING') AS paymentStatus,tk.id AS ticketId,tk.ticket_code AS ticketCode,
              tb.status AS boardingStatus,b.pickup_trip_stop_id AS pickupStopId,
              pl.name AS pickupName,dl.name AS dropoffName
            FROM bookings b JOIN booking_items bi ON bi.cancelled=FALSE AND bi.booking_id=b.id
            JOIN trip_stops ps ON ps.id=b.pickup_trip_stop_id JOIN locations pl ON pl.id=ps.location_id
            JOIN trip_stops ds ON ds.id=b.dropoff_trip_stop_id JOIN locations dl ON dl.id=ds.location_id
            LEFT JOIN tickets tk ON tk.replaced=FALSE AND tk.booking_item_id=bi.id
            LEFT JOIN payments p ON p.id=tk.payment_id
            LEFT JOIN ticket_boarding tb ON tb.booking_item_id=bi.id
            WHERE b.trip_id=? AND b.status IN ('PENDING','CONFIRMED','COMPLETED') ORDER BY ps.stop_order,bi.id
            """,(rs,n)-> {
                var row=new org.springframework.jdbc.core.ColumnMapRowMapper().mapRow(rs,n);
                row.put("paymentBlocked",rs.getBoolean("paymentBlocked"));
                row.put("pickupTime",com.busgo.common.time.BusGoTime.api(com.busgo.common.time.JpaJdbcTime.read(rs,"pickupTime")));
                return row;
            },tripId);
    }
    private void pickupWindow(Map<String,Object> t,long tripId,long stopId) {
        var stops=db.queryForList("SELECT * FROM trip_stops WHERE trip_id=? AND id=? AND allow_pickup=TRUE AND status='ACTIVE'",tripId,stopId);
        if(stops.isEmpty()) throw missing("PICKUP_STOP_NOT_FOUND");
        long origin=db.queryForObject("SELECT MIN(stop_order) FROM trip_stops WHERE trip_id=?",Long.class,tripId);
        boolean isOrigin=((Number)stops.get(0).get("stop_order")).longValue()==origin;
        if(!("BOARDING".equals(t.get("status")) || (!isOrigin && "DEPARTED".equals(t.get("status"))))) throw conflict("BOARDING_WINDOW_CLOSED","Pickup requires BOARDING, or DEPARTED at an intermediate stop.");
        if(db.queryForObject("SELECT COUNT(*) FROM trip_stop_operations WHERE trip_id=? AND stop_id=?",Long.class,tripId,stopId)>0) throw conflict("PICKUP_CLOSED","Pickup has been explicitly closed.");
    }
    public Map<String,Object> transition(CurrentUser actor,long tripId,long ticketId,String command,PickupContext input) {
        var t=trip(actor,tripId,true);
        var tickets=db.queryForList("SELECT tk.id,tk.status AS ticket_status,b.pickup_trip_stop_id,b.status AS booking_status,p.status AS payment_status FROM tickets tk JOIN bookings b ON b.id=tk.booking_id JOIN booking_items bi ON bi.cancelled=FALSE AND bi.id=tk.booking_item_id AND bi.booking_id=b.id JOIN trip_seats seat ON seat.id=bi.trip_seat_id AND seat.trip_id=b.trip_id JOIN payments p ON p.id=tk.payment_id AND p.booking_id=b.id WHERE tk.id=? AND b.trip_id=? FOR UPDATE",ticketId,tripId);
        if(tickets.isEmpty()) throw missing("TICKET_NOT_FOUND");
        var ticket=tickets.get(0);
        if(!"VALID".equals(ticket.get("ticket_status")) || !Set.of("CONFIRMED","COMPLETED").contains(ticket.get("booking_status")) || !"PAID".equals(ticket.get("payment_status"))) throw conflict("TICKET_NOT_ELIGIBLE","Successful payment and a valid booking are required.");
        if(((Number)ticket.get("pickup_trip_stop_id")).longValue()!=input.stopId()) throw conflict("WRONG_PICKUP_STOP","Use the ticket's booked pickup stop.");
        var states=db.queryForList("SELECT * FROM ticket_boarding WHERE ticket_id=? FOR UPDATE",ticketId);
        String state=states.isEmpty()?"EXPECTED":(String)states.get(0).get("status");
        String target=switch(command) { case "check-in"->"CHECKED_IN"; case "board","direct-board"->"BOARDED"; case "no-show"->"NO_SHOW"; default->throw new IllegalArgumentException("Unknown command"); };
        if(state.equals(target)) return boarding(ticketId);
        if(Set.of("BOARDED","NO_SHOW").contains(state)) throw conflict("INVALID_BOARDING_TRANSITION","Terminal attendance cannot change.");
        pickupWindow(t,tripId,input.stopId());
        if(command.equals("board") && !state.equals("CHECKED_IN")) throw conflict("CHECK_IN_REQUIRED","Check in before boarding, or use explicit direct-board.");
        if(states.isEmpty()) expectTicket(ticketId,input.stopId());
        if(command.equals("check-in") || (command.equals("direct-board") && state.equals("EXPECTED"))) {
            db.update("UPDATE ticket_boarding SET status='CHECKED_IN',checked_in_at=?,checked_in_by=?,version=version+1 WHERE ticket_id=?",now(),actor.id(),ticketId);
            history(tripId,"TICKET",ticketId,"CHECK_IN",actor.id(),input.reason());
        }
        if(target.equals("BOARDED")) {
            db.update("UPDATE ticket_boarding SET status='BOARDED',boarded_at=?,boarded_by=?,actual_boarding_stop_id=?,version=version+1 WHERE ticket_id=?",now(),actor.id(),input.stopId(),ticketId);
            history(tripId,"TICKET",ticketId,"BOARD",actor.id(),input.reason());
        } else if(target.equals("NO_SHOW")) {
            db.update("UPDATE ticket_boarding SET status='NO_SHOW',no_show_at=?,no_show_by=?,version=version+1 WHERE ticket_id=?",now(),actor.id(),ticketId);
            history(tripId,"TICKET",ticketId,"NO_SHOW",actor.id(),input.reason());
        }
        return boarding(ticketId);
    }
    private Map<String,Object> boarding(long ticket) { return db.queryForMap("SELECT * FROM ticket_boarding WHERE ticket_id=?",ticket); }
    public List<Map<String,Object>> pickups(CurrentUser actor,long tripId) {
        trip(actor,tripId,false);
        return db.queryForList("SELECT s.id AS stopId,l.name,s.stop_order AS stopOrder,o.pickup_closed_at AS closedAt FROM trip_stops s JOIN locations l ON l.id=s.location_id LEFT JOIN trip_stop_operations o ON o.trip_id=s.trip_id AND o.stop_id=s.id WHERE s.trip_id=? AND s.allow_pickup=TRUE AND s.status='ACTIVE' ORDER BY s.stop_order",tripId);
    }
    private long unresolved(long tripId,Long stopId) {
        return db.queryForObject("SELECT COUNT(*) FROM bookings b JOIN booking_items bi ON bi.cancelled=FALSE AND bi.booking_id=b.id LEFT JOIN ticket_boarding tb ON tb.booking_item_id=bi.id WHERE b.trip_id=? AND (? IS NULL OR b.pickup_trip_stop_id=?) AND b.status IN ('PENDING','CONFIRMED','COMPLETED') AND (tb.status IS NULL OR tb.status IN ('EXPECTED','CHECKED_IN'))",Long.class,tripId,stopId,stopId);
    }
    public List<Map<String,Object>> closePickup(CurrentUser actor,long tripId,long stopId,String reason) {
        var t=trip(actor,tripId,true);
        if(db.queryForObject("SELECT COUNT(*) FROM trip_stop_operations WHERE trip_id=? AND stop_id=?",Long.class,tripId,stopId)>0) return pickups(actor,tripId);
        pickupWindow(t,tripId,stopId);
        if(unresolved(tripId,stopId)>0) throw conflict("PICKUP_UNRESOLVED","Resolve every passenger before closing pickup: collect payment and board arrivals, or explicitly mark absent PAY_ON_BOARD reservations NO_SHOW.");
        db.update("INSERT INTO trip_stop_operations(trip_id,stop_id,pickup_closed_at,pickup_closed_by) VALUES(?,?,?,?)",tripId,stopId,now(),actor.id());
        history(tripId,"STOP",stopId,"PICKUP_CLOSED",actor.id(),reason);
        return pickups(actor,tripId);
    }
    public void requireOriginClosed(long tripId) {
        if(db.queryForObject("SELECT COUNT(*) FROM trip_stops s LEFT JOIN trip_stop_operations o ON o.trip_id=s.trip_id AND o.stop_id=s.id WHERE s.trip_id=? AND s.allow_pickup=TRUE AND s.status='ACTIVE' AND s.stop_order=(SELECT MIN(stop_order) FROM trip_stops WHERE trip_id=?) AND o.stop_id IS NULL",Long.class,tripId,tripId)>0)
            throw conflict("PICKUP_UNRESOLVED","Close origin pickup before departure.");
    }
    public void requireComplete(long tripId) {
        long open=db.queryForObject("SELECT COUNT(*) FROM trip_stops s LEFT JOIN trip_stop_operations o ON o.trip_id=s.trip_id AND o.stop_id=s.id WHERE s.trip_id=? AND s.allow_pickup=TRUE AND s.status='ACTIVE' AND o.stop_id IS NULL",Long.class,tripId);
        if(open>0 || unresolved(tripId,null)>0) throw conflict("PICKUP_UNRESOLVED","Close all pickup stops and resolve all passengers before completion.");
    }
    public List<Map<String,Object>> histories(CurrentUser actor,long tripId) {
        trip(actor,tripId,false);
        return db.queryForList("SELECT * FROM operational_history WHERE trip_id=? ORDER BY id",tripId);
    }
    public void history(long tripId,String entity,long entityId,String action,long actor,String reason) {
        db.update("INSERT INTO operational_history(trip_id,entity_type,entity_id,action,actor_id,occurred_at,reason) VALUES(?,?,?,?,?,?,?)",tripId,entity,entityId,action,actor,now(),reason);
    }
}
