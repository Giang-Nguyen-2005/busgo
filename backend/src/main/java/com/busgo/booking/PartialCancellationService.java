package com.busgo.booking;

import static com.busgo.booking.PartialCancellationDtos.*;
import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.common.time.JpaJdbcTime.parameter;
import com.busgo.booking.entity.*;
import com.busgo.booking.repository.BookingItemRepository;
import com.busgo.common.exception.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.entity.OperatorStatus;
import com.busgo.payment.BookingPaymentInventoryRepository;
import com.busgo.ticket.entity.Ticket;
import com.busgo.ticket.repository.TicketRepository;
import com.busgo.trip.entity.*;
import com.busgo.trip.search.TripSegmentResolver;
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
public class PartialCancellationService {
    private final ModificationService commerce;
    private final BookingItemRepository items;
    private final TicketRepository tickets;
    private final BookingPaymentInventoryRepository inventory;
    private final TripSegmentResolver segments;
    private final JdbcTemplate db;
    private final EntityManager em;
    private final Clock clock;
    public PartialCancellationService(ModificationService commerce,BookingItemRepository items,TicketRepository tickets,
            BookingPaymentInventoryRepository inventory,TripSegmentResolver segments,JdbcTemplate db,EntityManager em,Clock clock) {
        this.commerce=commerce; this.items=items; this.tickets=tickets; this.inventory=inventory;
        this.segments=segments; this.db=db; this.em=em; this.clock=clock;
    }
    private LocalDateTime now() { return utc(clock.instant()); }
    private static Rule yes() { return new Rule(true,null,null); }
    private static Rule no(String code,String message) { return new Rule(false,code,message); }
    private Rule rule(Booking b,boolean operator,boolean lock) {
        if(b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.CONFIRMED)
            return no("BOOKING_NOT_CANCELLABLE","Đặt vé không còn có thể hủy một phần.");
        if(b.getTrip().getStatus()!=TripStatus.SCHEDULED)
            return no("SOURCE_TRIP_CLOSED","Chuyến đã bắt đầu đón khách hoặc kết thúc.");
        if(b.getTrip().getOperatorRoute().getOperator().getStatus()!=OperatorStatus.ACTIVE
                || b.getTrip().getOperatorRoute().getStatus()!=com.busgo.common.entity.ActiveStatus.ACTIVE)
            return no("OPERATOR_INACTIVE","Nhà xe hoặc tuyến đang tạm ngưng.");
        if(b.getPickupTripStop().getPlannedDepartureTime()==null || !b.getPickupTripStop().getPlannedDepartureTime().isAfter(now()))
            return no("PICKUP_CLOSED","Đã qua giờ đón khách.");
        if(!operator && now().isAfter(b.getPickupTripStop().getPlannedDepartureTime().minusHours(6)))
            return no("CUSTOMER_CUTOFF","Không thể hủy một phần trong vòng 6 giờ trước giờ đón.");
        if(b.getStatus()==BookingStatus.PENDING && b.getPaymentDueAt()!=null && !b.getPaymentDueAt().isAfter(now()))
            return no("PAYMENT_EXPIRED","Đã hết hạn thanh toán đặt vé.");
        if(!db.queryForList("SELECT id FROM booking_modifications WHERE booking_id=? AND status IN ('HELD','AWAITING_PAYMENT')"+(lock?" FOR UPDATE":""),Long.class,b.getId()).isEmpty())
            return no("MODIFICATION_ACTIVE","Có thay đổi đang chờ. Hãy hoàn tất hoặc hủy thay đổi trước.");
        return yes();
    }
    private Set<Long> terminal(long id,boolean lock) {
        return new HashSet<>(db.queryForList("SELECT a.booking_item_id FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.booking_id=? AND a.status IN ('CHECKED_IN','BOARDED','NO_SHOW') ORDER BY a.id"+(lock?" FOR UPDATE":""),Long.class,id));
    }
    private boolean pickupClosed(Booking b,boolean lock) {
        return !db.queryForList("SELECT stop_id FROM trip_stop_operations WHERE trip_id=? AND stop_id=?"+(lock?" FOR UPDATE":""),Long.class,b.getTrip().getId(),b.getPickupTripStop().getId()).isEmpty();
    }
    private Item item(Booking b,BookingItem i,Set<Long> terminal) {
        Rule r=i.isCancelled()?no("ITEM_CANCELLED","Hành khách đã hủy."):terminal.contains(i.getId())?no("ATTENDANCE_CONFLICT","Hành khách đã điểm danh, lên xe hoặc vắng mặt."):yes();
        return new Item(i.getId(),i.getPassengerName()==null?b.getContactName():i.getPassengerName(),i.getSeatCode(),i.getUnitPrice(),i.isCancelled(),r);
    }
    private String journey(Booking b) { return b.getPickupTripStop().getLocation().getName()+" → "+b.getDropoffTripStop().getLocation().getName(); }
    @Transactional(readOnly=true)
    public Eligibility eligibility(CurrentUser actor,long id,boolean operator) {
        Booking b=commerce.owned(actor,id,operator,false); Rule r=rule(b,operator,false);
        var all=items.findDetailedByBookingId(id); var terminal=terminal(id,false);
        if(r.allowed() && pickupClosed(b,false)) r=no("PICKUP_CLOSED","Điểm đón đã đóng.");
        if(r.allowed() && all.stream().filter(i->!i.isCancelled()).count()<2) r=no("FULL_CANCELLATION_REQUIRED","Chỉ còn một hành khách. Hãy dùng hủy toàn bộ đặt vé.");
        if(r.allowed() && all.stream().noneMatch(i->!i.isCancelled() && !terminal.contains(i.getId()))) r=no("ATTENDANCE_CONFLICT","Không còn hành khách đủ điều kiện hủy.");
        if(operator && !actor.roles().contains(RoleCode.OPERATOR_ADMIN)) r=no("ADMIN_REQUIRED","Chỉ quản trị nhà xe có quyền hủy một phần.");
        return new Eligibility(r,b.getBookingCode(),journey(b),api(b.getPickupTripStop().getPlannedDepartureTime()),all.stream().map(i->item(b,i,terminal)).toList());
    }
    private record Plan(Booking booking,List<BookingItem> selected,List<Ticket> current,Quote quote) {}
    private Plan plan(CurrentUser actor,long id,boolean operator,Request request) {
        Booking initial=commerce.owned(actor,id,operator,true);
        Booking b=commerce.lock(initial,initial.getTrip().getId());
        Rule r=rule(b,operator,true); require(r);
        // Booking serialization is followed by current reads; no stale ownership snapshot is used for money.
        BigDecimal net=commerce.net(id);
        var current=tickets.findDetailedLockedByBookingId(id);
        for(var t:current) em.refresh(t,LockModeType.PESSIMISTIC_WRITE);
        Set<Long> terminal=terminal(id,true);
        if(pickupClosed(b,true)) throw conflict("PICKUP_CLOSED","Điểm đón đã đóng.");
        var all=items.lockByBookingId(id);
        for(var i:all) em.refresh(i,LockModeType.PESSIMISTIC_WRITE);
        var active=all.stream().filter(i->!i.isCancelled()).toList();
        var ids=request.bookingItemIds();
        if(ids==null || ids.isEmpty() || ids.size()>20 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size()!=ids.size())
            throw conflict("INVALID_SELECTION","Hãy chọn các hành khách khác nhau cần hủy.");
        List<BookingItem> selected=new ArrayList<>();
        for(long itemId:ids) {
            var i=all.stream().filter(it->it.getId()==itemId).findFirst().orElseThrow(PartialCancellationService::missing);
            require(item(b,i,terminal).eligibility()); selected.add(i);
        }
        if(selected.size()>=active.size()) throw conflict("FULL_CANCELLATION_REQUIRED","Hãy dùng hủy toàn bộ đặt vé để hủy tất cả hành khách còn lại.");
        BigDecimal total=active.stream().map(BookingItem::getUnitPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.compareTo(b.getTotalAmount())!=0 || net.signum()<0) throw inconsistent();
        var activeTickets=current.stream().filter(t->!t.getBookingItem().isCancelled()).toList();
        if(b.getStatus()==BookingStatus.CONFIRMED && (activeTickets.size()!=active.size() || activeTickets.stream().anyMatch(t->!"VALID".equals(t.getStatus())))) throw inconsistent();
        if(b.getStatus()==BookingStatus.PENDING && (net.signum()!=0 || !activeTickets.isEmpty())) throw inconsistent();
        BigDecimal cancelled=selected.stream().map(BookingItem::getUnitPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal next=total.subtract(cancelled);
        Quote q=new Quote(b.getBookingCode(),journey(b),api(b.getPickupTripStop().getPlannedDepartureTime()),selected.stream().map(i->item(b,i,terminal)).toList(),total,cancelled,next,net,net.subtract(next).max(BigDecimal.ZERO),next.subtract(net).max(BigDecimal.ZERO));
        return new Plan(b,selected,current,q);
    }
    public Quote quote(CurrentUser actor,long id,boolean operator,Request request) { return plan(actor,id,operator,request).quote(); }
    public History execute(CurrentUser actor,long id,boolean operator,Request request) {
        Plan p=plan(actor,id,operator,request); Booking b=p.booking(); Quote q=p.quote();
        var required=segments.resolve(b.getTrip().getId(),b.getPickupTripStop(),b.getDropoffTripStop()).stream().map(TripSegment::getId).toList();
        var rows=inventory.lockByBookingItems(p.selected().stream().map(BookingItem::getId).sorted().toList());
        if(required.isEmpty() || rows.size()!=p.selected().size()*required.size()) throw inconsistent();
        for(var i:p.selected()) {
            var owned=rows.stream().filter(row->i.getId().equals(row.bookingItemId())).toList();
            if(!i.getTripSeat().getTrip().getId().equals(b.getTrip().getId()) || !owned.stream().map(BookingPaymentInventoryRepository.BookedRow::tripSegmentId).toList().equals(required)
                || owned.stream().anyMatch(row->row.status()!=InventoryStatus.BOOKED || !row.tripSeatId().equals(i.getTripSeat().getId()) || row.holdToken()!=null || row.heldByUserId()!=null || row.holdExpiresAt()!=null)) throw inconsistent();
        }
        LocalDateTime time=now(); String code="PC-"+UUID.randomUUID();
        db.update("INSERT INTO partial_cancellations(code,booking_id,operator_id,actor_type,actor_user_id,actor_name,current_total,cancelled_amount,new_total,net_collected,refund_required,new_amount_due,trip_id,journey_name,departure_time,completed_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            code,id,b.getTrip().getOperatorRoute().getOperator().getId(),operator?"OPERATOR_ADMIN":"CUSTOMER",actor.id(),em.find(User.class,actor.id()).getFullName(),q.currentTotal(),q.cancelledAmount(),q.newTotal(),q.netCollected(),q.refundRequired(),q.newAmountDue(),b.getTrip().getId(),q.journeyName(),parameter(b.getPickupTripStop().getPlannedDepartureTime()),parameter(time));
        long cid=db.queryForObject("SELECT id FROM partial_cancellations WHERE code=?",Long.class,code);
        commerce.refund(id,q.refundRequired(),actor.id(),"PARTIAL_CANCELLATION",null,cid);
        for(var i:p.selected()) {
            Ticket ticket=p.current().stream().filter(t->t.getBookingItem().getId().equals(i.getId())).findFirst().orElse(null);
            db.update("INSERT INTO partial_cancellation_items(cancellation_id,booking_item_id,passenger_name,old_seat_id,seat_code,amount,ticket_id) VALUES(?,?,?,?,?,?,?)",cid,i.getId(),i.getPassengerName()==null?b.getContactName():i.getPassengerName(),i.getTripSeat().getId(),i.getSeatCode(),i.getUnitPrice(),ticket==null?null:ticket.getId());
            if(ticket!=null) { ticket.setStatus("VOID"); ticket.setVoidedAt(time); ticket.setVoidedByUserId(actor.id()); ticket.setVoidReason("PARTIAL_CANCELLATION"); }
            i.setCancelled(true);
        }
        for(var row:rows) if(db.update("UPDATE trip_seat_segment_inventory SET status='AVAILABLE',booking_item_id=NULL,version=version+1 WHERE id=? AND booking_item_id=? AND status='BOOKED'",row.id(),row.bookingItemId())!=1) throw inconsistent();
        b.setTotalAmount(q.newTotal()); b.setPaymentTokenHash(null);
        em.flush();
        return view(b,cid);
    }
    @Transactional(readOnly=true)
    public List<History> history(CurrentUser actor,long id,boolean operator) {
        Booking b=commerce.owned(actor,id,operator,false);
        return db.queryForList("SELECT id FROM partial_cancellations WHERE booking_id=? ORDER BY id DESC LIMIT 50",Long.class,id).stream().map(cid->view(b,cid)).toList();
    }
    @Transactional(readOnly=true)
    public History read(CurrentUser actor,long id,boolean operator,long cid) { return view(commerce.owned(actor,id,operator,false),cid); }
    private History view(Booking b,long cid) {
        var result=db.query("SELECT * FROM partial_cancellations WHERE id=? AND booking_id=?",(rs,n)-> {
            var selected=db.query("SELECT * FROM partial_cancellation_items WHERE cancellation_id=? ORDER BY booking_item_id",(r,k)->new Item(r.getLong("booking_item_id"),r.getString("passenger_name"),r.getString("seat_code"),r.getBigDecimal("amount"),true,no("ITEM_CANCELLED","Hành khách đã hủy.")),cid);
            Quote q=new Quote(b.getBookingCode(),rs.getString("journey_name"),api(com.busgo.common.time.JpaJdbcTime.read(rs,"departure_time")),selected,rs.getBigDecimal("current_total"),rs.getBigDecimal("cancelled_amount"),rs.getBigDecimal("new_total"),rs.getBigDecimal("net_collected"),rs.getBigDecimal("refund_required"),rs.getBigDecimal("new_amount_due"));
            return new History(cid,rs.getString("code"),rs.getString("actor_type"),rs.getString("actor_name"),api(com.busgo.common.time.JpaJdbcTime.read(rs,"completed_at")),q);
        },cid,b.getId());
        if(result.isEmpty()) throw missing(); return result.get(0);
    }
    private static void require(Rule r) { if(!r.allowed()) throw conflict(r.reasonCode(),r.message()); }
    private static BusinessException conflict(String code,String message) { return new BusinessException(code,message,HttpStatus.CONFLICT,null); }
    private static BusinessException inconsistent() { return conflict("PARTIAL_CANCELLATION_INCONSISTENT","Trạng thái đặt vé không nhất quán. Vui lòng liên hệ hỗ trợ."); }
    private static ResourceNotFoundException missing() { return new ResourceNotFoundException("BOOKING_NOT_FOUND","Không tìm thấy đặt vé hoặc hành khách."); }
}
