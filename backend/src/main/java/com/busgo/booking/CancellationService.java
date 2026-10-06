package com.busgo.booking;

import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.common.time.JpaJdbcTime.parameter;
import static com.busgo.booking.CancellationDtos.*;
import com.busgo.booking.entity.*;
import com.busgo.booking.repository.*;
import com.busgo.common.exception.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import com.busgo.operator.entity.OperatorStatus;
import com.busgo.operator.repository.TransportOperatorRepository;
import com.busgo.payment.BookingPaymentInventoryRepository;
import com.busgo.payment.entity.*;
import com.busgo.ticket.repository.TicketRepository;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.TripRepository;
import com.busgo.trip.search.TripSegmentResolver;
import com.busgo.user.entity.User;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancellationService {
    @org.springframework.beans.factory.annotation.Autowired
    private ModificationService modifications;
    private final BookingRepository bookings;
    private final TripRepository trips;
    private final TransportOperatorRepository operators;
    private final BookingItemRepository items;
    private final TicketRepository tickets;
    private final BookingStatusHistoryRepository histories;
    private final BookingPaymentInventoryRepository inventory;
    private final TripSegmentResolver segments;
    private final OperatorContextService context;
    private final EntityManager em;
    private final JdbcTemplate db;
    private final Clock clock;

    public CancellationService(BookingRepository bookings, TripRepository trips,
            TransportOperatorRepository operators, BookingItemRepository items, TicketRepository tickets,
            BookingStatusHistoryRepository histories, BookingPaymentInventoryRepository inventory,
            TripSegmentResolver segments, OperatorContextService context, EntityManager em,
            JdbcTemplate db, Clock clock) {
        this.bookings=bookings; this.trips=trips; this.operators=operators; this.items=items;
        this.tickets=tickets; this.histories=histories; this.inventory=inventory;
        this.segments=segments; this.context=context; this.em=em; this.db=db; this.clock=clock;
    }

    private Booking owned(CurrentUser user, long id, boolean operator) {
        if (!operator) return bookings.findOwnedById(id,user.id())
                .filter(b->b.getSource()==BookingSource.WEB).orElseThrow(CancellationService::missing);
        Long owner=context.requireOperatorMember(user).getId();
        return bookings.findById(id).filter(b->b.getTrip().getOperatorRoute().getOperator().getId().equals(owner))
                .orElseThrow(CancellationService::missing);
    }

    @Transactional(readOnly=true)
    public Recovery customerRead(CurrentUser user,long id) { return view(owned(user,id,false),false); }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAnyRole('OPERATOR_ADMIN','OPERATOR_STAFF')")
    public Recovery operatorRead(CurrentUser user,long id) { return view(owned(user,id,true),true); }

    @Transactional
    @PreAuthorize("hasRole('CUSTOMER')")
    public Recovery customerCancel(CurrentUser user,long id,Request request) {
        Booking b=owned(user,id,false);
        return cancel(b,user.id(),"CUSTOMER_CANCELLED",request.note(),false,false);
    }

    @Transactional
    @PreAuthorize("hasRole('OPERATOR_ADMIN')")
    public Recovery operatorCancel(CurrentUser user,long id,Request request) {
        context.requireAdminOperator(user);
        return cancel(owned(user,id,true),user.id(),"OPERATOR_CANCELLED",request.note(),true,false);
    }

    // Invoked by the scheduled job through the transactional service proxy, one booking at a time.
    @Transactional
    public boolean expire(long id) {
        Booking b=bookings.findById(id).orElse(null);
        if(b==null) return false;
        Recovery result=cancel(b,null,"PAYMENT_TIMEOUT",null,true,true);
        return result!=null;
    }

    private Recovery cancel(Booking initial,Long actor,String reason,String note,boolean operator,boolean expiry) {
        long sourceTripId=initial.getTrip().getId();
        Trip trip=trips.lockById(sourceTripId).orElseThrow(CancellationService::missing);
        var owner=operators.lockById(trip.getOperatorRoute().getOperator().getId()).orElseThrow(CancellationService::missing);
        Booking b=bookings.lockById(initial.getId()).orElseThrow(CancellationService::missing);
        em.refresh(trip,LockModeType.PESSIMISTIC_WRITE); em.refresh(owner,LockModeType.PESSIMISTIC_WRITE);
        em.refresh(b,LockModeType.PESSIMISTIC_WRITE);
        if(b.getTrip().getId()!=sourceTripId) throw conflict("BOOKING_CHANGED","Booking trip changed; retry.");
        var paymentRows=db.queryForList("SELECT id,status,amount,paid_at FROM payments WHERE booking_id=? ORDER BY id FOR UPDATE",b.getId());
        LocalDateTime now=utc(clock.instant());
        if (expiry && (b.getStatus()!=BookingStatus.PENDING || b.getPaymentDueAt()==null
                || b.getPaymentDueAt().isAfter(now) || b.getPaymentMethod()==PaymentMethod.PAY_ON_BOARD
                || !paymentRows.isEmpty())) return null;
        if(b.getStatus()==BookingStatus.CANCELLED) return view(b,operator);
        String blocked=blocked(b,operator,expiry,now,true);
        if(blocked!=null) {
            if(expiry) return null;
            throw conflict(blocked,"Booking cancellation is unavailable: "+blocked);
        }
        boolean paid=b.getStatus()==BookingStatus.CONFIRMED;
        java.math.BigDecimal collected=paymentRows.stream().filter(p->"PAID".equals(p.get("status"))).map(p->(java.math.BigDecimal)p.get("amount")).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
        java.math.BigDecimal refunded=collected.subtract(modifications.net(b.getId()));
        var issued=tickets.findDetailedLockedByBookingId(b.getId()).stream().filter(t->!t.getBookingItem().isCancelled()).toList();
        var allItems=items.lockByBookingId(b.getId());
        for(var i:allItems) em.refresh(i,LockModeType.PESSIMISTIC_WRITE);
        var bookingItems=allItems.stream().filter(i->!i.isCancelled()).toList();
        if(bookingItems.isEmpty() || (paid && (paymentRows.isEmpty() || paymentRows.stream().anyMatch(p->!"PAID".equals(p.get("status")))
                || paymentRows.get(0).get("paid_at")==null
                || b.getTotalAmount().compareTo(collected.subtract(refunded))!=0
                || issued.size()!=bookingItems.size())) || (!paid && (!paymentRows.isEmpty() || !issued.isEmpty())))
            throw conflict("CANCELLATION_STATE_INCONSISTENT","Payment/ticket state is inconsistent.");
        if(paid && issued.stream().anyMatch(t->!"VALID".equals(t.getStatus())
                || !t.getPayment().getId().equals(((Number)paymentRows.get(0).get("id")).longValue())
                || bookingItems.stream().noneMatch(i->i.getId().equals(t.getBookingItem().getId()))))
            throw conflict("CANCELLATION_STATE_INCONSISTENT","Issued ticket set is inconsistent.");
        var required=segments.resolve(trip.getId(),b.getPickupTripStop(),b.getDropoffTripStop()).stream().map(TripSegment::getId).toList();
        var rows=inventory.lockByBookingItems(bookingItems.stream().map(BookingItem::getId).toList());
        if(required.isEmpty() || rows.size()!=bookingItems.size()*required.size()) throw inconsistent();
        for(var item:bookingItems) {
            if(!trip.getId().equals(item.getTripSeat().getTrip().getId())) throw inconsistent();
            var allocated=rows.stream().filter(r->item.getId().equals(r.bookingItemId())).toList();
            if(!allocated.stream().map(BookingPaymentInventoryRepository.BookedRow::tripSegmentId).toList().equals(required)
                    || allocated.stream().anyMatch(r->r.status()!=InventoryStatus.BOOKED || !r.tripSeatId().equals(item.getTripSeat().getId())
                    || r.holdToken()!=null || r.heldByUserId()!=null || r.holdExpiresAt()!=null)) throw inconsistent();
        }
        // All allocations validated before the first write; a failure rolls back every domain change.
        for(var row:rows) if(db.update("UPDATE trip_seat_segment_inventory SET status='AVAILABLE',booking_item_id=NULL,version=version+1 WHERE id=? AND booking_item_id=? AND status='BOOKED'",row.id(),row.bookingItemId())!=1) throw inconsistent();
        if(paid) {
            modifications.refund(b.getId(),b.getTotalAmount(),actor,reason,null);
            for(var row:paymentRows) {
                Payment payment=em.find(Payment.class,((Number)row.get("id")).longValue(),LockModeType.PESSIMISTIC_WRITE);
                em.refresh(payment,LockModeType.PESSIMISTIC_WRITE); payment.setStatus(PaymentStatus.REFUNDED);
            }
            for(var ticket:issued) {
                ticket.setStatus("VOID"); ticket.setVoidedAt(now); ticket.setVoidedByUserId(actor); ticket.setVoidReason(reason);
            }
        }
        BookingStatus previous=b.getStatus();
        b.setStatus(BookingStatus.CANCELLED); b.setCancelledAt(now); b.setCancelledByUserId(actor);
        b.setCancellationReason(reason); b.setCancellationNote(note);
        BookingStatusHistory history=new BookingStatusHistory(); history.setBooking(b); history.setFromStatus(previous);
        history.setToStatus(BookingStatus.CANCELLED); history.setChangedBy(actor==null?null:em.getReference(User.class,actor));
        history.setReasonCode(reason); history.setNote(note); history.setChangedAt(now); histories.save(history);
        em.flush();
        return view(b,operator);
    }

    private String blocked(Booking b,boolean operator,boolean expiry,LocalDateTime now,boolean lock) {
        if(b.getStatus()!=BookingStatus.PENDING && b.getStatus()!=BookingStatus.CONFIRMED) return "BOOKING_NOT_CANCELLABLE";
        if(b.getTrip().getStatus()!=TripStatus.SCHEDULED && b.getTrip().getStatus()!=TripStatus.BOARDING) return "CANCELLATION_WINDOW_CLOSED";
        var attendance=db.queryForList("SELECT a.status FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.cancelled=FALSE AND i.booking_id=? ORDER BY a.id"+(lock?" FOR UPDATE":""),String.class,b.getId());
        if(attendance.stream().anyMatch(s->Set.of("CHECKED_IN","BOARDED","NO_SHOW").contains(s))) return "CANCELLATION_ATTENDANCE_CONFLICT";
        if(!db.queryForList("SELECT stop_id FROM trip_stop_operations WHERE trip_id=? AND stop_id=?"+(lock?" FOR UPDATE":""),Long.class,b.getTrip().getId(),b.getPickupTripStop().getId()).isEmpty()) return "PICKUP_CLOSED";
        if(!expiry && b.getTrip().getOperatorRoute().getOperator().getStatus()!=OperatorStatus.ACTIVE
                && (operator || b.getStatus()==BookingStatus.CONFIRMED)) return "CANCELLATION_OPERATOR_SUSPENDED";
        if(!operator && (b.getPickupTripStop().getPlannedDepartureTime()==null
                || now.isAfter(b.getPickupTripStop().getPlannedDepartureTime().minusHours(6)))) return "CUSTOMER_CANCELLATION_CUTOFF";
        return null;
    }

    private Recovery view(Booking b,boolean operator) {
        String blocked=blocked(b,operator,false,utc(clock.instant()),false);
        var refunds=db.query("SELECT r.* FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=? ORDER BY r.id",
                (rs,n)->new Refund(rs.getLong("id"),rs.getLong("payment_id"),rs.getBigDecimal("amount"),api(com.busgo.common.time.JpaJdbcTime.read(rs,"refunded_at")),rs.getObject("refunded_by",Long.class),rs.getString("reason_code"),rs.getString("note")),b.getId());
        var ticketRows=db.query("SELECT id,ticket_code,status,voided_at FROM tickets WHERE booking_id=? ORDER BY id",
                (rs,n)->new Ticket(rs.getLong("id"),rs.getString("ticket_code"),rs.getString("status"),api(com.busgo.common.time.JpaJdbcTime.read(rs,"voided_at"))),b.getId());
        var history=db.query("SELECT * FROM booking_status_history WHERE booking_id=? ORDER BY changed_at,id",
                (rs,n)->new History(rs.getString("from_status"),rs.getString("to_status"),rs.getObject("changed_by_user_id",Long.class),rs.getString("reason_code"),rs.getString("note"),api(com.busgo.common.time.JpaJdbcTime.read(rs,"changed_at"))),b.getId());
        return new Recovery(b.getId(),b.getStatus(),blocked==null,blocked,b.getStatus()==BookingStatus.CONFIRMED || !refunds.isEmpty(),
                api(b.getPaymentDueAt()),api(b.getPickupTripStop().getPlannedDepartureTime()==null?null:b.getPickupTripStop().getPlannedDepartureTime().minusHours(6)),
                api(b.getCancelledAt()),b.getCancelledByUserId(),b.getCancellationReason(),b.getCancellationNote(),refunds,ticketRows,history);
    }
    private static ResourceNotFoundException missing() { return new ResourceNotFoundException("BOOKING_NOT_FOUND","Booking was not found."); }
    private static BusinessException conflict(String code,String message) { return new BusinessException(code,message,HttpStatus.CONFLICT,null); }
    private static BusinessException inconsistent() { return conflict("BOOKING_INVENTORY_INCONSISTENT","Complete booked allocation is required for cancellation."); }
}
