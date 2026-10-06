package com.busgo.booking;

import static com.busgo.booking.ModificationDtos.*;
import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.common.time.JpaJdbcTime.parameter;
import com.busgo.booking.entity.*;
import com.busgo.booking.repository.*;
import com.busgo.common.exception.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.hold.SeatHoldInventoryRepository;
import com.busgo.operator.OperatorContextService;
import com.busgo.operator.repository.TransportOperatorRepository;
import com.busgo.payment.entity.*;
import com.busgo.payment.repository.PaymentRepository;
import com.busgo.ticket.entity.Ticket;
import com.busgo.ticket.repository.TicketRepository;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.trip.search.*;
import com.busgo.trip.availability.*;
import com.busgo.user.entity.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModificationService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.busgo.notification.NotificationService notifications;
    @org.springframework.beans.factory.annotation.Autowired
    private com.busgo.payment.BookingPaymentInventoryRepository bookedInventory;
    @org.springframework.beans.factory.annotation.Autowired
    private TripSegmentResolver segmentResolver;
    private final BookingRepository bookings;
    private final BookingItemRepository items;
    private final TripRepository trips;
    private final TransportOperatorRepository operators;
    private final TripSeatRepository seats;
    private final CustomerJourneyResolver journeys;
    private final SeatAvailabilityService availability;
    private final SeatHoldInventoryRepository inventory;
    private final TicketRepository tickets;
    private final PaymentRepository payments;
    private final OperatorContextService context;
    private final JdbcTemplate db;
    private final EntityManager em;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Value("${busgo.booking.modification-hold-duration:PT10M}")
    private Duration duration=Duration.ofMinutes(10);

    public ModificationService(BookingRepository bookings, BookingItemRepository items, TripRepository trips,
            TransportOperatorRepository operators, TripSeatRepository seats, CustomerJourneyResolver journeys,
            SeatAvailabilityService availability, SeatHoldInventoryRepository inventory, TicketRepository tickets,
            PaymentRepository payments, OperatorContextService context, JdbcTemplate db, EntityManager em, Clock clock) {
        this.bookings=bookings; this.items=items; this.trips=trips; this.operators=operators; this.seats=seats;
        this.journeys=journeys; this.availability=availability; this.inventory=inventory; this.tickets=tickets;
        this.payments=payments; this.context=context; this.db=db; this.em=em; this.clock=clock;
    }
    private LocalDateTime now() { return utc(clock.instant()); }
    Booking owned(CurrentUser actor,long id,boolean operator,boolean mutation) {
        if(actor==null || actor.roles().contains(RoleCode.SYSTEM_ADMIN)) throw denied();
        if(operator) {
            long owner=(mutation?context.requireAdminOperator(actor):context.requireOperatorMember(actor)).getId();
            return bookings.findById(id).filter(b->b.getTrip().getOperatorRoute().getOperator().getId()==owner).orElseThrow(ModificationService::missing);
        }
        if(!actor.roles().contains(RoleCode.CUSTOMER)) throw denied();
        return bookings.findOwnedById(id,actor.id()).filter(b->b.getSource()==BookingSource.WEB).orElseThrow(ModificationService::missing);
    }
    // All trip locks precede operator/booking locks, regardless of direction. Never lock a newly discovered trip here.
    Booking lock(Booking initial,long target) {
        long source=initial.getTrip().getId();
        for(long id:new TreeSet<>(List.of(source,target))) {
            Trip t=trips.lockById(id).orElseThrow(ModificationService::missing);
            em.refresh(t,LockModeType.PESSIMISTIC_WRITE);
        }
        var owner=operators.lockById(initial.getTrip().getOperatorRoute().getOperator().getId()).orElseThrow(ModificationService::missing);
        em.refresh(owner,LockModeType.PESSIMISTIC_WRITE);
        Booking b=bookings.lockById(initial.getId()).orElseThrow(ModificationService::missing);
        em.refresh(b,LockModeType.PESSIMISTIC_WRITE);
        if(b.getTrip().getId()!=source) throw conflict("BOOKING_CHANGED","Đặt vé vừa thay đổi. Vui lòng tải lại.");
        return b;
    }
    private Rule rule(Booking b,boolean operator,boolean active) {
        if(b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.CONFIRMED)
            return no("BOOKING_NOT_MODIFIABLE","Đặt vé không còn có thể thay đổi.");
        if(b.getTrip().getStatus()!=TripStatus.SCHEDULED)
            return no("SOURCE_TRIP_CLOSED","Chuyến đã bắt đầu đón khách hoặc kết thúc.");
        if(b.getTrip().getOperatorRoute().getOperator().getStatus()!=com.busgo.operator.entity.OperatorStatus.ACTIVE)
            return no("OPERATOR_INACTIVE","Nhà xe đang tạm ngưng hoạt động.");
        if(b.getPickupTripStop().getPlannedDepartureTime()==null || !b.getPickupTripStop().getPlannedDepartureTime().isAfter(now()))
            return no("PICKUP_CLOSED","Đã qua giờ đón khách.");
        if(!operator && now().isAfter(b.getPickupTripStop().getPlannedDepartureTime().minusHours(6)))
            return no("CUSTOMER_CUTOFF","Không thể thay đổi trong vòng 6 giờ trước giờ đón.");
        if(b.getStatus()==BookingStatus.PENDING && b.getPaymentDueAt()!=null && !b.getPaymentDueAt().isAfter(now()))
            return no("PAYMENT_EXPIRED","Đã hết hạn thanh toán đặt vé.");
        if(db.queryForObject("SELECT COUNT(*) FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.cancelled=FALSE AND i.booking_id=? AND a.status IN ('CHECKED_IN','BOARDED','NO_SHOW')",Long.class,b.getId())>0)
            return no("ATTENDANCE_CONFLICT","Có khách đã điểm danh, lên xe hoặc được ghi nhận vắng mặt.");
        if(db.queryForObject("SELECT COUNT(*) FROM trip_stop_operations WHERE trip_id=? AND stop_id=?",Long.class,b.getTrip().getId(),b.getPickupTripStop().getId())>0)
            return no("PICKUP_CLOSED","Điểm đón đã đóng.");
        if(active && db.queryForObject("SELECT COUNT(*) FROM booking_modifications WHERE booking_id=? AND status IN ('HELD','AWAITING_PAYMENT')",Long.class,b.getId())>0)
            return no("MODIFICATION_ACTIVE","Có thay đổi đang chờ xác nhận. Hãy tiếp tục hoặc hủy thay đổi đó.");
        return new Rule(true,null,null);
    }
    private static Rule no(String code,String message) { return new Rule(false,code,message); }
    private void eligible(Booking b,boolean operator,boolean active) {
        // Current locking reads, not an earlier repeatable-read snapshot, decide races.
        if(active && !db.queryForList("SELECT id FROM booking_modifications WHERE booking_id=? AND status IN ('HELD','AWAITING_PAYMENT') FOR UPDATE",Long.class,b.getId()).isEmpty())
            throw conflict("MODIFICATION_ACTIVE","Có thay đổi đang chờ xác nhận. Hãy tiếp tục hoặc hủy thay đổi đó.");
        db.queryForList("SELECT id FROM payments WHERE booking_id=? ORDER BY id FOR UPDATE",Long.class,b.getId());
        tickets.findDetailedLockedByBookingId(b.getId());
        if(!db.queryForList("SELECT a.id FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.cancelled=FALSE AND i.booking_id=? AND a.status IN ('CHECKED_IN','BOARDED','NO_SHOW') FOR UPDATE",Long.class,b.getId()).isEmpty())
            throw conflict("ATTENDANCE_CONFLICT","Có khách đã điểm danh, lên xe hoặc được ghi nhận vắng mặt.");
        if(!db.queryForList("SELECT stop_id FROM trip_stop_operations WHERE trip_id=? AND stop_id=? FOR UPDATE",Long.class,b.getTrip().getId(),b.getPickupTripStop().getId()).isEmpty())
            throw conflict("PICKUP_CLOSED","Điểm đón đã đóng.");
        Rule r=rule(b,operator,false); if(!r.allowed()) throw conflict(r.reasonCode(),r.message());
    }

    @Transactional(readOnly=true)
    public Eligibility eligibility(CurrentUser actor,long id,boolean operator) {
        Booking b=owned(actor,id,operator,false); Rule r=rule(b,operator,true);
        if(operator && !actor.roles().contains(RoleCode.OPERATOR_ADMIN)) r=no("ADMIN_REQUIRED","Chỉ quản trị nhà xe có quyền hỗ trợ thay đổi.");
        return new Eligibility(r,r,b.getBookingCode(),b.getSource().name(),b.getContactName(),b.getTrip().getId(),
                items.findActiveByBookingId(id).stream().map(i->new CurrentItem(i.getId(),i.getTripSeat().getId(),i.getSeatCode())).toList());
    }
    private CustomerJourneyResolver.ResolvedJourney target(Booking b,long tripId) {
        Trip t=trips.findById(tripId).orElseThrow(ModificationService::missing);
        if(!t.getOperatorRoute().getOperator().getId().equals(b.getTrip().getOperatorRoute().getOperator().getId())) throw missing();
        return journeys.resolve(tripId,b.getPickupTripStop().getLocation().getId(),b.getDropoffTripStop().getLocation().getId());
    }
    @Transactional(readOnly=true)
    public SeatAvailabilityDtos.SeatMap seatMap(CurrentUser actor,long id,boolean operator,long tripId) {
        Booking b=owned(actor,id,operator,false); target(b,tripId);
        return availability.get(tripId,b.getPickupTripStop().getLocation().getId(),b.getDropoffTripStop().getLocation().getId());
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> alternatives(CurrentUser actor,long id,boolean operator) {
        Booking b=owned(actor,id,operator,false);
        var result=db.query("""
            SELECT t.id AS tripId,p.planned_departure_time AS departureTime,l.name AS pickupName,dloc.name AS dropoffName,f.price
            FROM trips t JOIN operator_routes o ON o.id=t.operator_route_id
            JOIN trip_stops p ON p.trip_id=t.id AND p.location_id=? AND p.allow_pickup=TRUE AND p.status='ACTIVE'
            JOIN trip_stops d ON d.trip_id=t.id AND d.location_id=? AND d.allow_dropoff=TRUE AND d.status='ACTIVE'
            JOIN locations l ON l.id=p.location_id JOIN locations dloc ON dloc.id=d.location_id
            JOIN operator_route_fares f ON f.operator_route_id=o.id AND f.from_route_stop_id=p.source_route_stop_id AND f.to_route_stop_id=d.source_route_stop_id AND f.status='ACTIVE'
            WHERE o.operator_id=? AND o.status='ACTIVE' AND t.status='SCHEDULED' AND t.id<>? AND p.stop_order<d.stop_order
            AND p.planned_departure_time>? AND p.planned_departure_time<? ORDER BY p.planned_departure_time,t.id LIMIT 50
            """,(rs,n)-> {
                Map<String,Object> row=new org.springframework.jdbc.core.ColumnMapRowMapper().mapRow(rs,n);
                row.put("departureTime",api(com.busgo.common.time.JpaJdbcTime.read(rs,"departureTime")));
                return row;
            },b.getPickupTripStop().getLocation().getId(),b.getDropoffTripStop().getLocation().getId(),b.getTrip().getOperatorRoute().getOperator().getId(),b.getTrip().getId(),parameter(now()),parameter(now().plusDays(60)));
        return result;
    }
    public Quote quote(CurrentUser actor,long id,boolean operator,Request input) {
        Booking b=lock(owned(actor,id,operator,true),input.targetTripId()); eligible(b,operator,true);
        var plan=plan(b,input);
        var rows=inventory.lockRequired(plan.quote().items().stream().map(Item::newSeatId).toList(),plan.journey().requiredSegments().stream().map(TripSegment::getId).toList());
        if(rows.size()!=input.items().size()*plan.journey().requiredSegmentCount() || rows.stream().anyMatch(r->r.status()!=InventoryStatus.AVAILABLE && !(r.status()==InventoryStatus.HELD && r.holdExpiresAt()!=null && !r.holdExpiresAt().isAfter(now())))) throw conflict("SEAT_NOT_AVAILABLE","Ghế đã được đặt hoặc đang được giữ.");
        return plan.quote();
    }
    private record Plan(Quote quote,CustomerJourneyResolver.ResolvedJourney journey,List<BookingItem> all) {}
    private Plan plan(Booking b,Request input) {
        boolean same=b.getTrip().getId().equals(input.targetTripId());
        if((input.type()==Type.SEAT_CHANGE)!=same) throw conflict("INVALID_MODIFICATION","Loại thay đổi không phù hợp với chuyến đã chọn.");
        var journey=target(b,input.targetTripId()); var all=currentItems(b.getId());
        if(input.items()==null || input.items().isEmpty() || input.items().size()>20) throw conflict("INVALID_SELECTION","Hãy chọn ghế cần đổi.");
        if(new HashSet<>(input.items().stream().map(Selection::bookingItemId).toList()).size()!=input.items().size()
            || new HashSet<>(input.items().stream().map(Selection::targetSeatId).toList()).size()!=input.items().size()) throw conflict("DUPLICATE_SEAT","Không được chọn trùng ghế hoặc khách.");
        if(!same && input.items().size()!=all.size()) throw conflict("WHOLE_BOOKING_REQUIRED","Đổi chuyến cần chọn đủ ghế cho toàn bộ đặt vé.");
        var targetSeats=seats.findByTripIdAndIdInOrderByIdAsc(input.targetTripId(),input.items().stream().map(Selection::targetSeatId).toList());
        if(targetSeats.size()!=input.items().size()) throw conflict("INVALID_SEAT","Ghế không thuộc chuyến đã chọn.");
        List<Item> changes=new ArrayList<>();
        for(var selection:input.items()) {
            var item=all.stream().filter(i->i.getId().equals(selection.bookingItemId())).findFirst().orElseThrow(ModificationService::missing);
            if(all.stream().anyMatch(i->i.getTripSeat().getId().equals(selection.targetSeatId()))) throw conflict("SEAT_NOT_AVAILABLE","Hãy chọn ghế mới chưa thuộc đặt vé này.");
            var seat=targetSeats.stream().filter(s->s.getId().equals(selection.targetSeatId())).findFirst().orElseThrow();
            changes.add(new Item(item.getId(),item.getTripSeat().getId(),item.getSeatCode(),seat.getId(),seat.getSeatCode(),item.getUnitPrice(),same?item.getUnitPrice():journey.fare().getPrice()));
        }
        BigDecimal newTotal=same?b.getTotalAmount():journey.fare().getPrice().multiply(BigDecimal.valueOf(all.size()));
        BigDecimal collected=net(b.getId());
        if((b.getStatus()==BookingStatus.CONFIRMED && collected.compareTo(b.getTotalAmount())!=0) || (b.getStatus()==BookingStatus.PENDING && collected.signum()!=0)) throw conflict("PAYMENT_INCONSISTENT","Số tiền thanh toán không khớp đặt vé.");
        var money=ModificationMoney.quote(b.getTotalAmount(),newTotal,collected,b.getStatus()==BookingStatus.CONFIRMED);
        var q=new Quote(input.type(),b.getTrip().getId(),input.targetTripId(),b.getBookingCode(),b.getPickupTripStop().getLocation().getName()+" → "+b.getDropoffTripStop().getLocation().getName(),api(b.getPickupTripStop().getPlannedDepartureTime()),api(journey.pickup().getPlannedDepartureTime()),money,List.copyOf(changes));
        return new Plan(q,journey,all);
    }
    private List<BookingItem> currentItems(long id) {
        var all=items.lockByBookingId(id);
        for(var i:all) em.refresh(i,LockModeType.PESSIMISTIC_WRITE);
        return all.stream().filter(i->!i.isCancelled()).toList();
    }
    BigDecimal net(long id) {
        return refundablePayments(id).stream().map(p->decimal(p,"remaining")).reduce(BigDecimal.ZERO,BigDecimal::add);
    }
    private List<Map<String,Object>> refundablePayments(long id) {
        var rows=db.queryForList("SELECT * FROM payments WHERE booking_id=? AND status IN ('PAID','REFUNDED') ORDER BY id FOR UPDATE",id);
        for(var p:rows) {
            BigDecimal refunded=db.queryForList("SELECT amount FROM refunds WHERE payment_id=? ORDER BY id FOR UPDATE",BigDecimal.class,p.get("id")).stream().reduce(BigDecimal.ZERO,BigDecimal::add);
            p.put("remaining",decimal(p,"amount").subtract(refunded));
        }
        return rows;
    }
    public History create(CurrentUser actor,long id,boolean operator,Request input) {
        Booking b=lock(owned(actor,id,operator,true),input.targetTripId()); eligible(b,operator,true);
        Plan plan=plan(b,input); Quote q=plan.quote();
        var segmentIds=plan.journey().requiredSegments().stream().map(TripSegment::getId).toList();
        var rows=inventory.lockRequired(q.items().stream().map(Item::newSeatId).toList(),segmentIds);
        LocalDateTime time=now();
        if(rows.size()!=q.items().size()*segmentIds.size() || rows.stream().anyMatch(r->r.status()!=InventoryStatus.AVAILABLE && !(r.status()==InventoryStatus.HELD && r.holdExpiresAt()!=null && !r.holdExpiresAt().isAfter(time)))) throw conflict("SEAT_NOT_AVAILABLE","Ghế vừa được đặt hoặc đang được giữ.");
        if(duration.isNegative() || duration.isZero()) throw new IllegalStateException("Modification hold duration must be positive");
        LocalDateTime expires=time.plus(duration); String token=UUID.randomUUID().toString(),code="MOD-"+UUID.randomUUID().toString();
        inventory.markHeld(rows.stream().map(SeatHoldInventoryRepository.LockedInventory::id).toList(),token,actor.id(),expires,time);
        String name=em.find(User.class,actor.id()).getFullName();
        db.update("""
            INSERT INTO booking_modifications(code,booking_id,operator_id,type,status,source_trip_id,target_trip_id,target_pickup_id,target_dropoff_id,source_departure,target_departure,journey_name,old_total,new_total,net_collected,collection_required,refund_required,actor_type,actor_user_id,actor_name,hold_token,expires_at,created_at,updated_at)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,code,id,b.getTrip().getOperatorRoute().getOperator().getId(),q.type().name(),q.money().collectionRequired().signum()>0?"AWAITING_PAYMENT":"HELD",q.sourceTripId(),q.targetTripId(),plan.journey().pickup().getId(),plan.journey().dropoff().getId(),parameter(b.getPickupTripStop().getPlannedDepartureTime()),parameter(plan.journey().pickup().getPlannedDepartureTime()),q.journeyName(),q.money().currentTotal(),q.money().newTotal(),q.money().alreadyCollected(),q.money().collectionRequired(),q.money().refundRequired(),operator?"OPERATOR_ADMIN":"CUSTOMER",actor.id(),name,token,parameter(expires),parameter(time),parameter(time));
        long mid=db.queryForObject("SELECT id FROM booking_modifications WHERE code=?",Long.class,code);
        var currentTickets=tickets.findDetailedByBookingId(id);
        for(Item i:q.items()) db.update("INSERT INTO booking_modification_items(modification_id,booking_item_id,old_seat_id,new_seat_id,old_seat_label,new_seat_label,old_fare,new_fare,old_ticket_id) VALUES(?,?,?,?,?,?,?,?,?)",mid,i.bookingItemId(),i.oldSeatId(),i.newSeatId(),i.oldSeatLabel(),i.newSeatLabel(),i.oldFare(),i.newFare(),currentTickets.stream().filter(t->t.getBookingItem().getId()==i.bookingItemId()).map(Ticket::getId).findFirst().orElse(null));
        return read(actor,id,operator,mid);
    }
    @Transactional(readOnly=true)
    public List<History> history(CurrentUser actor,long id,boolean operator) {
        Booking b=owned(actor,id,operator,false);
        return db.queryForList("SELECT id FROM booking_modifications WHERE booking_id=? ORDER BY id DESC LIMIT 50",Long.class,id).stream().map(mid->view(b,mid)).toList();
    }
    @Transactional(readOnly=true)
    public History read(CurrentUser actor,long id,boolean operator,long mid) { return view(owned(actor,id,operator,false),mid); }
    private Map<String,Object> modification(long id,long mid,boolean lock) {
        var rows=db.query("SELECT * FROM booking_modifications WHERE booking_id=? AND id=?"+(lock?" FOR UPDATE":""),(rs,n)->{
            Map<String,Object> row=new org.springframework.jdbc.core.ColumnMapRowMapper().mapRow(rs,n);
            for(String key:List.of("source_departure","target_departure","expires_at","completed_at","created_at","updated_at")) row.put(key,com.busgo.common.time.JpaJdbcTime.read(rs,key));
            return row;
        },id,mid);
        if(rows.isEmpty()) throw missing(); return rows.get(0);
    }
    private List<Item> changes(long mid) {
        return db.query("SELECT * FROM booking_modification_items WHERE modification_id=? ORDER BY booking_item_id",(r,n)->new Item(r.getLong("booking_item_id"),r.getLong("old_seat_id"),r.getString("old_seat_label"),r.getLong("new_seat_id"),r.getString("new_seat_label"),r.getBigDecimal("old_fare"),r.getBigDecimal("new_fare")),mid);
    }
    private History view(Booking b,long mid) {
        var m=modification(b.getId(),mid,false);
        var money=ModificationMoney.quote(decimal(m,"old_total"),decimal(m,"new_total"),decimal(m,"net_collected"),decimal(m,"net_collected").signum()>0);
        Quote q=new Quote(Type.valueOf((String)m.get("type")),number(m,"source_trip_id"),number(m,"target_trip_id"),b.getBookingCode(),(String)m.get("journey_name"),api(time(m,"source_departure")),api(time(m,"target_departure")),money,changes(mid));
        return new History(mid,(String)m.get("code"),(String)m.get("status"),q,(String)m.get("actor_type"),(String)m.get("actor_name"),api(time(m,"created_at")),api(time(m,"expires_at")),api(time(m,"completed_at")));
    }
    public History cancel(CurrentUser actor,long id,boolean operator,long mid) {
        Booking initial=owned(actor,id,operator,true); var before=modification(id,mid,false);
        Booking b=lock(initial,number(before,"target_trip_id")); var m=modification(id,mid,true);
        if(active(m)) finishHold(mid,m,!time(m,"expires_at").isAfter(now())?"EXPIRED":"CANCELLED");
        return view(b,mid);
    }
    public boolean expire(long mid) {
        var ids=db.queryForList("SELECT booking_id FROM booking_modifications WHERE id=?",Long.class,mid);
        if(ids.isEmpty()) return false;
        var m=modification(ids.get(0),mid,false); lock(bookings.findById(ids.get(0)).orElseThrow(),number(m,"target_trip_id"));
        m=modification(ids.get(0),mid,true);
        if(!active(m) || time(m,"expires_at").isAfter(now())) return false;
        finishHold(mid,m,"EXPIRED"); return true;
    }
    private static boolean active(Map<String,Object> m) { return Set.of("HELD","AWAITING_PAYMENT").contains(m.get("status")); }
    private void finishHold(long mid,Map<String,Object> m,String status) {
        inventory.releaseOwned((String)m.get("hold_token"),number(m,"actor_user_id"),now());
        db.update("UPDATE booking_modifications SET status=?,updated_at=? WHERE id=?",status,parameter(now()),mid);
    }
    public History confirm(CurrentUser actor,long id,boolean operator,long mid) {
        Booking initial=owned(actor,id,operator,true); var before=modification(id,mid,false);
        Booking b=lock(initial,number(before,"target_trip_id")); var m=modification(id,mid,true);
        if("COMPLETED".equals(m.get("status"))) return view(b,mid);
        if(!active(m)) throw conflict("MODIFICATION_CLOSED","Thay đổi đã kết thúc.");
        if(!time(m,"expires_at").isAfter(now())) { finishHold(mid,m,"EXPIRED"); return view(b,mid); }
        eligible(b,operator,false);
        if(b.getTrip().getId()!=number(m,"source_trip_id") || b.getTotalAmount().compareTo(decimal(m,"old_total"))!=0 || net(id).compareTo(decimal(m,"net_collected"))!=0)
            throw conflict("BOOKING_CHANGED","Đặt vé hoặc thanh toán đã thay đổi. Hãy hủy yêu cầu và thử lại.");
        var journey=target(b,number(m,"target_trip_id")); var changes=changes(mid); var all=currentItems(id);
        var paymentRows=db.queryForList("SELECT id FROM payments WHERE booking_id=? ORDER BY id FOR UPDATE",Long.class,id);
        var currentTickets=tickets.findDetailedLockedByBookingId(id);
        db.queryForList("SELECT a.id FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.cancelled=FALSE AND i.booking_id=? ORDER BY a.id FOR UPDATE",id);
        // Source allocation is checked as well as target ownership before any money or ticket write.
        var sourceSegments=segmentResolver.resolve(b.getTrip().getId(),b.getPickupTripStop(),b.getDropoffTripStop()).stream().map(TripSegment::getId).toList();
        var sourceRows=bookedInventory.lockByBookingItems(all.stream().map(BookingItem::getId).toList());
        if(sourceRows.size()!=all.size()*sourceSegments.size()) throw conflict("BOOKING_INVENTORY_INCONSISTENT","Chỗ đã đặt không còn nhất quán.");
        for(var i:all) {
            var allocated=sourceRows.stream().filter(r->i.getId().equals(r.bookingItemId())).toList();
            if(!allocated.stream().map(com.busgo.payment.BookingPaymentInventoryRepository.BookedRow::tripSegmentId).toList().equals(sourceSegments)
                || allocated.stream().anyMatch(r->r.status()!=InventoryStatus.BOOKED || !r.tripSeatId().equals(i.getTripSeat().getId()) || r.holdToken()!=null))
                throw conflict("BOOKING_INVENTORY_INCONSISTENT","Chỗ đã đặt không còn nhất quán.");
        }
        var targetSegments=journey.requiredSegments().stream().map(TripSegment::getId).toList();
        var held=inventory.lockRequired(changes.stream().map(Item::newSeatId).toList(),targetSegments);
        var ownedRows=db.queryForList("SELECT id FROM trip_seat_segment_inventory WHERE hold_token=? AND held_by_user_id=? AND status='HELD' AND hold_expires_at>? ORDER BY id FOR UPDATE",Long.class,m.get("hold_token"),m.get("actor_user_id"),now());
        if(held.size()!=changes.size()*targetSegments.size() || !new HashSet<>(ownedRows).equals(new HashSet<>(held.stream().map(SeatHoldInventoryRepository.LockedInventory::id).toList()))) throw conflict("SEAT_NOT_AVAILABLE","Ghế giữ đã hết hạn hoặc không còn khả dụng.");
        Long adjustmentPaymentId=null;
        if(decimal(m,"collection_required").signum()>0) {
            Payment p=new Payment(); p.setBooking(b); p.setMethod(PaymentMethod.MOCK_ONLINE); p.setAmount(decimal(m,"collection_required")); p.setStatus(PaymentStatus.PAID); p.setPurpose("BOOKING_MODIFICATION"); p.setModificationId(mid); p.setPaidAt(now()); p.setTransactionReference("MOD-MOCK-"+UUID.randomUUID()); payments.saveAndFlush(p);
            adjustmentPaymentId=p.getId();
        }
        refund(id,decimal(m,"refund_required"),actor.id(),"MODIFICATION_FARE_DIFFERENCE",mid);
        for(Item change:changes) {
            BookingItem item=all.stream().filter(i->i.getId()==change.bookingItemId()).findFirst().orElseThrow();
            if(item.getTripSeat().getId()!=change.oldSeatId() || item.getUnitPrice().compareTo(change.oldFare())!=0) throw conflict("BOOKING_CHANGED","Ghế nguồn đã thay đổi.");
            int released=db.update("UPDATE trip_seat_segment_inventory SET status='AVAILABLE',booking_item_id=NULL,version=version+1 WHERE booking_item_id=? AND status='BOOKED'",item.getId());
            if(released!=sourceSegments.size()) throw conflict("BOOKING_INVENTORY_INCONSISTENT","Chỗ đã đặt không còn nhất quán.");
            int assigned=db.update("UPDATE trip_seat_segment_inventory SET status='BOOKED',booking_item_id=?,hold_token=NULL,held_by_user_id=NULL,hold_expires_at=NULL,version=version+1 WHERE trip_seat_id=? AND hold_token=? AND status='HELD'",item.getId(),change.newSeatId(),m.get("hold_token"));
            if(assigned!=targetSegments.size()) throw conflict("SEAT_NOT_AVAILABLE","Không thể chuyển chỗ đã giữ.");
            item.setTripSeat(em.getReference(TripSeat.class,change.newSeatId())); item.setSeatCode(change.newSeatLabel()); item.setUnitPrice(change.newFare());
            var old=currentTickets.stream().filter(t->t.getBookingItem().getId().equals(item.getId())).findFirst();
            if(old.isPresent()) {
                Ticket t=old.get(); if(!"VALID".equals(t.getStatus())) throw conflict("TICKET_INCONSISTENT","Vé không còn hợp lệ.");
                t.setStatus("VOID"); t.setVoidedAt(now()); t.setVoidedByUserId(actor.id()); t.setVoidReason("BOOKING_MODIFICATION"); t.setReplaced(true); em.flush();
                Ticket replacement=new Ticket(); replacement.setTicketCode("TKT-"+UUID.randomUUID()); replacement.setBooking(b); replacement.setBookingItem(item); replacement.setPayment(t.getPayment()); replacement.setPassengerName(t.getPassengerName()); replacement.setSeatCode(item.getSeatCode()); tickets.saveAndFlush(replacement);
                db.update("UPDATE ticket_boarding SET ticket_id=?,pickup_stop_id=?,version=version+1 WHERE booking_item_id=? AND status='EXPECTED'",replacement.getId(),journey.pickup().getId(),item.getId());
                db.update("UPDATE booking_modification_items SET new_ticket_id=? WHERE modification_id=? AND booking_item_id=?",replacement.getId(),mid,item.getId());
            }
        }
        b.setTrip(journey.trip()); b.setPickupTripStop(journey.pickup()); b.setDropoffTripStop(journey.dropoff()); b.setTotalAmount(decimal(m,"new_total")); b.setPaymentTokenHash(null);
        if(b.getPaymentDueAt()!=null && b.getPaymentDueAt().isAfter(journey.pickup().getPlannedDepartureTime())) b.setPaymentDueAt(journey.pickup().getPlannedDepartureTime());
        db.update("UPDATE booking_modifications SET status='COMPLETED',completed_at=?,updated_at=? WHERE id=?",parameter(now()),parameter(now()),mid);
        em.flush();
        notifications.record(b, com.busgo.notification.NotificationType.BOOKING_MODIFIED, Long.toString(mid));
        if(adjustmentPaymentId!=null)
            notifications.record(b, com.busgo.notification.NotificationType.PAYMENT_SUCCEEDED, adjustmentPaymentId.toString());
        return view(b,mid);
    }
    // Reused by cancellation; caller holds trip/operator/booking and payment locks.
    public void refund(long bookingId,BigDecimal requested,Long actor,String reason,Long mid) {
        refund(bookingId,requested,actor,reason,mid,null);
    }
    public void refund(long bookingId,BigDecimal requested,Long actor,String reason,Long mid,Long partialId) {
        BigDecimal remaining=requested;
        var rows=refundablePayments(bookingId);
        for(var p:rows) {
            BigDecimal amount=remaining.min(decimal(p,"remaining")); if(amount.signum()<=0) continue;
            db.update("INSERT INTO refunds(payment_id,amount,payment_paid_at,refunded_at,refunded_by,reason_code,modification_id,partial_cancellation_id,created_at) VALUES(?,?,?,?,?,?,?,?,?)",p.get("id"),amount,p.get("paid_at"),parameter(now()),actor,reason,mid,partialId,parameter(now()));
            remaining=remaining.subtract(amount);
        }
        if(remaining.signum()!=0) throw conflict("PAYMENT_INCONSISTENT","Không đủ số tiền đã thu để hoàn.");
    }
    private static long number(Map<String,Object> m,String key) { return ((Number)m.get(key)).longValue(); }
    private static BigDecimal decimal(Map<String,Object> m,String key) { return (BigDecimal)m.get(key); }
    private static LocalDateTime time(Map<String,Object> m,String key) { Object v=m.get(key); return v==null?null:v instanceof java.sql.Timestamp t?t.toLocalDateTime():(LocalDateTime)v; }
    private static ResourceNotFoundException missing() { return new ResourceNotFoundException("BOOKING_NOT_FOUND","Không tìm thấy đặt vé hoặc thay đổi."); }
    private static BusinessException conflict(String code,String message) { return new BusinessException(code,message,HttpStatus.CONFLICT,null); }
    private static BusinessException denied() { return new BusinessException("ACCESS_DENIED","Không có quyền thay đổi đặt vé.",HttpStatus.FORBIDDEN,null); }
}
