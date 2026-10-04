package com.busgo.fleet;

import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.fleet.MaintenanceDtos.*;
import static com.busgo.fleet.FleetMaintenanceGuard.conflict;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.common.time.JpaJdbcTime;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.BusRepository;
import com.busgo.operator.OperatorContextService;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class MaintenanceService {
    private final JdbcTemplate db;
    private final OperatorContextService context;
    private final FleetMaintenanceGuard guard;
    private final BusRepository buses;
    private final Clock clock;
    public MaintenanceService(JdbcTemplate db,OperatorContextService context,FleetMaintenanceGuard guard,BusRepository buses,Clock clock) {
        this.db=db;this.context=context;this.guard=guard;this.buses=buses;this.clock=clock;
    }
    private LocalDateTime now() { return utc(clock.instant()); }
    private LocalDate today() { return clock.instant().atZone(BUSINESS_ZONE).toLocalDate(); }
    private long operator(CurrentUser actor) { return context.requireAdminOperator(actor).getId(); }
    private Bus owned(long operator,long id) { return buses.findOwnedById(id,operator).orElseThrow(()->new ResourceNotFoundException("BUS_NOT_FOUND","Bus was not found.")); }
    private static BusinessException invalid(String message) { return new BusinessException("VALIDATION_ERROR",message,HttpStatus.BAD_REQUEST,null); }
    private static final String SELECT="SELECT m.*,b.license_plate FROM bus_maintenance_records m JOIN buses b ON b.id=m.bus_id WHERE b.operator_id=? AND b.deleted_at IS NULL";
    private Maintenance row(ResultSet r,int n) throws SQLException {
        return new Maintenance(r.getLong("id"),r.getLong("bus_id"),r.getString("license_plate"),Type.valueOf(r.getString("maintenance_type")),Status.valueOf(r.getString("status")),r.getString("title"),r.getString("note"),time(r,"scheduled_start"),time(r,"scheduled_end"),time(r,"started_at"),time(r,"completed_at"),time(r,"cancelled_at"),r.getObject("odometer_km",Long.class),r.getObject("next_due_date",LocalDate.class),r.getObject("next_due_odometer_km",Long.class),r.getString("completion_note"),r.getString("cancellation_reason"),r.getLong("created_by"),r.getObject("completed_by",Long.class),r.getObject("cancelled_by",Long.class),time(r,"created_at"),time(r,"updated_at"));
    }
    private static OffsetDateTime time(ResultSet r,String key) throws SQLException { return api(JpaJdbcTime.read(r,key)); }
    private Maintenance record(long operator,long id,boolean lock) {
        var rows=db.query(SELECT+" AND m.id=?"+(lock?" FOR UPDATE":""),this::row,operator,id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("MAINTENANCE_NOT_FOUND","Maintenance was not found.");
        return rows.get(0);
    }
    public PagedResponse<Maintenance> list(CurrentUser actor,Long bus,Status status,Type type,LocalDate from,LocalDate to,int page,int size) {
        long operator=operator(actor);
        if(bus!=null) owned(operator,bus);
        if(page<0 || page>100000 || size<1 || size>100 || from!=null && to!=null && to.isBefore(from)) throw invalid("Invalid filters or pagination.");
        var args=new ArrayList<Object>();args.add(operator);
        String where="";
        if(bus!=null) {where+=" AND m.bus_id=?";args.add(bus);}
        if(status!=null) {where+=" AND m.status=?";args.add(status.name());}
        if(type!=null) {where+=" AND m.maintenance_type=?";args.add(type.name());}
        if(from!=null) {where+=" AND m.scheduled_start>=?";args.add(JpaJdbcTime.parameter(businessDate(from).startInclusive()));}
        if(to!=null) {where+=" AND m.scheduled_start<?";args.add(JpaJdbcTime.parameter(businessDate(to).endExclusive()));}
        long count=db.queryForObject("SELECT COUNT(*) FROM ("+SELECT+where+") counted",Long.class,args.toArray());
        args.add(size);args.add(page*size);
        var rows=db.query(SELECT+where+" ORDER BY CASE WHEN m.status='IN_PROGRESS' THEN 0 WHEN m.status='SCHEDULED' THEN 1 ELSE 2 END, CASE WHEN m.status IN ('IN_PROGRESS','SCHEDULED') THEN m.scheduled_start END ASC, COALESCE(m.completed_at,m.cancelled_at,m.created_at) DESC,m.id DESC LIMIT ? OFFSET ?",this::row,args.toArray());
        return new PagedResponse<>(rows,new PagedResponse.Pagination(page,size,count,(int)((count+size-1)/size)));
    }
    private List<AssignedTrip> trips(long operator,long bus) {
        return db.query("SELECT t.id,r.name,t.departure_time,t.estimated_arrival_time,t.status FROM trips t JOIN operator_routes o ON o.id=t.operator_route_id JOIN routes r ON r.id=o.route_id WHERE o.operator_id=? AND t.bus_id=? AND t.status IN ('SCHEDULED','BOARDING','DEPARTED') ORDER BY t.departure_time DESC,t.id DESC",(r,n)->new AssignedTrip(r.getLong("id"),r.getString("name"),time(r,"departure_time"),time(r,"estimated_arrival_time"),r.getString("status")),operator,bus);
    }
    public PagedResponse<AssignedTrip> assignedTrips(CurrentUser actor,long bus,int page,int size) {
        long operator=operator(actor);owned(operator,bus);
        long count=db.queryForObject("SELECT COUNT(*) FROM trips t JOIN operator_routes o ON o.id=t.operator_route_id WHERE o.operator_id=? AND t.bus_id=?",Long.class,operator,bus);
        var rows=db.query("SELECT t.id,r.name,t.departure_time,t.estimated_arrival_time,t.status FROM trips t JOIN operator_routes o ON o.id=t.operator_route_id JOIN routes r ON r.id=o.route_id WHERE o.operator_id=? AND t.bus_id=? ORDER BY CASE WHEN t.status IN ('SCHEDULED','BOARDING','DEPARTED') THEN 0 ELSE 1 END, CASE WHEN t.status IN ('SCHEDULED','BOARDING','DEPARTED') THEN t.departure_time END ASC,t.departure_time DESC,t.id DESC LIMIT ? OFFSET ?",(r,n)->new AssignedTrip(r.getLong("id"),r.getString("name"),time(r,"departure_time"),time(r,"estimated_arrival_time"),r.getString("status")),operator,bus,size,page*size);
        return new PagedResponse<>(rows,new PagedResponse.Pagination(page,size,count,(int)((count+size-1)/size)));
    }
    private void tripConflicts(long operator,long bus,LocalDateTime start,LocalDateTime end,boolean starting) {
        var conflicts=trips(operator,bus).stream().filter(t->Set.of("SCHEDULED","BOARDING","DEPARTED").contains(t.status()))
                .filter(t->starting && Set.of("BOARDING","DEPARTED").contains(t.status()) || MaintenanceRules.overlaps(start,end,t.departureTime().toLocalDateTime(),t.estimatedArrivalTime().toLocalDateTime())).toList();
        if(!conflicts.isEmpty()) throw conflict("MAINTENANCE_TRIP_CONFLICT","Xe đã được phân công cho chuyến trùng lịch. Chọn thời gian bảo trì khác; phân công xe của chuyến cố định sau khi tạo.",Map.of("trips",conflicts));
    }
    public Maintenance schedule(CurrentUser actor,long busId,Schedule input) {
        long operator=operator(actor);
        LocalDateTime start=utc(input.scheduledStart().toInstant()),end=utc(input.scheduledEnd().toInstant());
        if(!end.isAfter(start) || start.isBefore(now()) || input.title().isBlank()) throw invalid("Maintenance requires a future start and end after start.");
        guard.lockFleetBus(operator,busId);
        tripConflicts(operator,busId,start,end,false);
        guard.requireAssignable(busId,start,end);
        var timestamp=JpaJdbcTime.parameter(now());
        db.update("INSERT INTO bus_maintenance_records(bus_id,maintenance_type,status,title,note,scheduled_start,scheduled_end,created_by,created_at,updated_at) VALUES(?,?,'SCHEDULED',?,?,?,?,?,?,?)",busId,input.maintenanceType().name(),input.title().strip(),input.note(),JpaJdbcTime.parameter(start),JpaJdbcTime.parameter(end),actor.id(),timestamp,timestamp);
        return record(operator,db.queryForObject("SELECT LAST_INSERT_ID()",Long.class),false);
    }
    private Maintenance lock(CurrentUser actor,long operator,long id) {
        Maintenance probe=record(operator,id,false);
        guard.lockFleetBus(operator,probe.busId());
        return record(operator,id,true);
    }
    private void transition(Maintenance m,Status target) {
        if(!MaintenanceRules.allowed(m.status(),target)) throw conflict("INVALID_MAINTENANCE_TRANSITION","Không thể chuyển trạng thái bảo trì từ "+m.status()+" sang "+target+".",null);
    }
    public Maintenance start(CurrentUser actor,long id) {
        long operator=operator(actor);Maintenance m=lock(actor,operator,id);transition(m,Status.IN_PROGRESS);
        if(!m.scheduledEnd().toLocalDateTime().isAfter(now())) throw conflict("MAINTENANCE_WINDOW_ENDED","Lịch bảo trì đã kết thúc. Hủy và tạo lịch mới.",null);
        LocalDateTime start=now().isBefore(m.scheduledStart().toLocalDateTime())?now():m.scheduledStart().toLocalDateTime();
        tripConflicts(operator,m.busId(),start,m.scheduledEnd().toLocalDateTime(),true);
        if(guard.hasActive(m.busId())) throw conflict("BUS_MAINTENANCE_CONFLICT","Xe đang có bảo trì khác.",null);
        long plannedConflict=db.queryForObject("SELECT COUNT(*) FROM bus_maintenance_records WHERE bus_id=? AND id<>? AND status='SCHEDULED' AND scheduled_start<? AND scheduled_end>?",Long.class,m.busId(),id,JpaJdbcTime.parameter(m.scheduledEnd().toLocalDateTime()),JpaJdbcTime.parameter(start));
        if(plannedConflict>0) throw conflict("BUS_MAINTENANCE_CONFLICT","Bắt đầu sớm sẽ trùng lịch bảo trì khác.",null);
        Bus bus=owned(operator,m.busId());
        db.update("UPDATE bus_maintenance_records SET status='IN_PROGRESS',started_at=?,previous_bus_status=?,updated_at=? WHERE id=?",JpaJdbcTime.parameter(now()),bus.getStatus().name(),JpaJdbcTime.parameter(now()),id);
        guard.changeStatus(bus,BusStatus.MAINTENANCE,"MAINTENANCE_STARTED",id,actor.id(),now());
        return record(operator,id,false);
    }
    public Maintenance complete(CurrentUser actor,long id,Complete input) {
        long operator=operator(actor);Maintenance m=lock(actor,operator,id);transition(m,Status.COMPLETED);
        if(input.odometerKm()!=null && input.odometerKm()<0 || input.nextDueOdometerKm()!=null && (input.nextDueOdometerKm()<0 || input.odometerKm()!=null && input.nextDueOdometerKm()<=input.odometerKm()) || input.nextDueDate()!=null && input.nextDueDate().isBefore(today())) throw invalid("Next due values must be on/after today and above recorded odometer.");
        String prior=db.queryForObject("SELECT previous_bus_status FROM bus_maintenance_records WHERE id=?",String.class,id);
        db.update("UPDATE bus_maintenance_records SET status='COMPLETED',completed_at=?,completed_by=?,odometer_km=?,next_due_date=?,next_due_odometer_km=?,completion_note=?,updated_at=? WHERE id=?",JpaJdbcTime.parameter(now()),actor.id(),input.odometerKm(),input.nextDueDate(),input.nextDueOdometerKm(),input.note(),JpaJdbcTime.parameter(now()),id);
        Bus bus=owned(operator,m.busId());
        // Manual INACTIVE changes are never undone. Manual MAINTENANCE remains unavailable.
        if(bus.getStatus()==BusStatus.MAINTENANCE && "AVAILABLE".equals(prior)) guard.changeStatus(bus,BusStatus.AVAILABLE,"MAINTENANCE_COMPLETED",id,actor.id(),now());
        else if(bus.getStatus()==BusStatus.MAINTENANCE && "INACTIVE".equals(prior)) guard.changeStatus(bus,BusStatus.INACTIVE,"MAINTENANCE_COMPLETED",id,actor.id(),now());
        return record(operator,id,false);
    }
    public Maintenance cancel(CurrentUser actor,long id,Cancel input) {
        long operator=operator(actor);Maintenance m=lock(actor,operator,id);transition(m,Status.CANCELLED);
        db.update("UPDATE bus_maintenance_records SET status='CANCELLED',cancelled_at=?,cancelled_by=?,cancellation_reason=?,updated_at=? WHERE id=?",JpaJdbcTime.parameter(now()),actor.id(),input.reason(),JpaJdbcTime.parameter(now()),id);
        return record(operator,id,false);
    }
    public Readiness readiness(long operator,Bus bus) {
        var records=db.query(SELECT+" AND m.bus_id=? AND (m.status IN ('SCHEDULED','IN_PROGRESS') OR m.id=(SELECT latest.id FROM bus_maintenance_records latest WHERE latest.bus_id=m.bus_id AND latest.maintenance_type=m.maintenance_type AND latest.status='COMPLETED' ORDER BY latest.completed_at DESC,latest.id DESC LIMIT 1)) ORDER BY m.scheduled_start,m.id",this::row,operator,bus.getId());
        var active=records.stream().filter(m->m.status()==Status.IN_PROGRESS).findFirst().orElse(null);
        var next=records.stream().filter(m->m.status()==Status.SCHEDULED).findFirst().orElse(null);
        // Only a later completed record of the same maintenance type supersedes its due metadata.
        var dueDates=records.stream().filter(m->m.status()==Status.COMPLETED).map(Maintenance::nextDueDate).filter(Objects::nonNull).toList();
        LocalDate due=dueDates.stream().min(Comparator.naturalOrder()).orElse(null);
        var assigned=trips(operator,bus.getId()).stream().filter(t->Set.of("SCHEDULED","BOARDING","DEPARTED").contains(t.status())).sorted(Comparator.comparing(AssignedTrip::departureTime)).toList();
        var warnings=new ArrayList<String>();
        if(active!=null || bus.getStatus()==BusStatus.MAINTENANCE) warnings.add("Đang bảo trì");
        if(bus.getStatus()==BusStatus.INACTIVE) warnings.add("Ngừng hoạt động");
        if(next!=null && !next.scheduledStart().toLocalDateTime().isBefore(now()) && !next.scheduledStart().toLocalDateTime().isAfter(now().plusDays(7)) || dueDates.stream().anyMatch(d->MaintenanceRules.dueSoon(d,today()))) warnings.add("Bảo trì sắp tới");
        if(MaintenanceRules.overdue(due,today())) warnings.add("Bảo trì quá hạn");
        if(assigned.stream().anyMatch(t->!guard.warning(bus.getId(),t.departureTime().toLocalDateTime(),t.estimatedArrivalTime().toLocalDateTime()).isEmpty())) warnings.add("Trùng kế hoạch chuyến");
        boolean plannedNow=records.stream().anyMatch(m->m.status()==Status.SCHEDULED && !now().isBefore(m.scheduledStart().toLocalDateTime()) && now().isBefore(m.scheduledEnd().toLocalDateTime()));
        if(plannedNow) warnings.add("Trong lịch bảo trì");
        boolean ready=MaintenanceRules.ready(bus.getStatus(),bus.getBusType().getStatus()==com.busgo.common.entity.ActiveStatus.ACTIVE,active!=null,plannedNow);
        if(bus.getBusType().getStatus()!=com.busgo.common.entity.ActiveStatus.ACTIVE) warnings.add("Loại xe ngừng hoạt động");
        return new Readiness(ready,bus.getStatus(),active!=null?"IN_PROGRESS":next!=null?"SCHEDULED":"NONE",active==null?null:active.id(),next==null?null:next.scheduledStart(),next==null?null:next.maintenanceType(),due,assigned.isEmpty()?null:assigned.get(0),List.copyOf(warnings));
    }
    public List<StatusHistory> history(CurrentUser actor,long bus) {
        long operator=operator(actor);owned(operator,bus);
        return db.query("SELECT h.* FROM bus_status_history h JOIN buses b ON b.id=h.bus_id WHERE b.operator_id=? AND b.id=? ORDER BY h.changed_at DESC,h.id DESC LIMIT 100",(r,n)->new StatusHistory(r.getLong("id"),BusStatus.valueOf(r.getString("previous_status")),BusStatus.valueOf(r.getString("new_status")),r.getString("reason_code"),r.getObject("maintenance_id",Long.class),r.getLong("changed_by"),time(r,"changed_at")),operator,bus);
    }
    public FleetWarnings warnings(CurrentUser actor) {
        long operator=operator(actor);long available=0,maintenance=0,inactive=0,soon=0,overdue=0;
        var notReady=new ArrayList<AssignedTrip>();
        // Bounded pages, no global cross-operator bus scan.
        int page=0;org.springframework.data.domain.Page<Bus> rows;
        do {
            rows=buses.search(operator,null,null,"",org.springframework.data.domain.PageRequest.of(page++,100));
            for(Bus bus:rows) {
                switch(bus.getStatus()) {case AVAILABLE -> available++;case MAINTENANCE -> maintenance++;case INACTIVE -> inactive++;}
                var r=readiness(operator,bus);if(r.warnings().contains("Bảo trì sắp tới")) soon++;if(r.warnings().contains("Bảo trì quá hạn")) overdue++;
                for(var t:trips(operator,bus.getId())) if(Set.of("SCHEDULED","BOARDING").contains(t.status()) && !t.departureTime().toLocalDateTime().isBefore(now()) && !t.departureTime().toLocalDateTime().isAfter(now().plusDays(7)) && (!r.operationalReady() || !guard.warning(bus.getId(),t.departureTime().toLocalDateTime(),t.estimatedArrivalTime().toLocalDateTime()).isEmpty())) notReady.add(t);
            }
        } while(rows.hasNext());
        notReady.sort(Comparator.comparing(AssignedTrip::departureTime));
        return new FleetWarnings(available,maintenance,inactive,soon,overdue,notReady.stream().limit(20).toList());
    }
}
