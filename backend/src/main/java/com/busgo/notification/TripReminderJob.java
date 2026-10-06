package com.busgo.notification;

import static com.busgo.common.time.BusGoTime.utc;
import static com.busgo.common.time.JpaJdbcTime.parameter;
import com.busgo.booking.entity.*;
import com.busgo.trip.entity.TripStatus;
import com.busgo.payment.entity.PaymentMethod;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TripReminderJob {
    private final JdbcTemplate db;
    private final EntityManager em;
    private final NotificationService notifications;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public TripReminderJob(JdbcTemplate db,EntityManager em,NotificationService notifications,Clock clock,PlatformTransactionManager manager) {
        this.db=db; this.em=em; this.notifications=notifications; this.clock=clock;
        transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    @Scheduled(fixedDelayString="${busgo.notifications.reminder-interval:PT5M}",initialDelayString="${busgo.notifications.initial-delay:PT1M}")
    public void run() { runAt(clock.instant()); }
    public void runAt(Instant instant) {
        var now=utc(instant);
        for(int hours:new int[]{24,2}) {
            var type=hours==24?NotificationType.TRIP_REMINDER_24H:NotificationType.TRIP_REMINDER_2H;
            var ids=db.queryForList("""
                SELECT b.id FROM bookings b JOIN trip_stops s ON s.id=b.pickup_trip_stop_id
                WHERE (b.status='CONFIRMED' OR (b.status='PENDING' AND b.source='PHONE' AND b.payment_method='PAY_ON_BOARD'))
                AND (b.customer_id IS NOT NULL OR b.contact_email REGEXP '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$')
                AND EXISTS (SELECT 1 FROM booking_items i WHERE i.booking_id=b.id AND i.cancelled=FALSE)
                AND s.planned_departure_time>? AND s.planned_departure_time<=?
                AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.booking_id=b.id AND n.event_type=?)
                ORDER BY s.planned_departure_time,b.id LIMIT 100
                """,Long.class,parameter(now.plusHours(hours).minusMinutes(15)),parameter(now.plusHours(hours)),type.name());
            for(long id:ids) {
                try { transaction.executeWithoutResult(status->remind(id,type,hours,now)); }
                catch(org.springframework.dao.DataAccessException failure) { /* Retry on the next bounded scan. */ }
            }
        }
    }
    private void remind(long id,NotificationType type,int hours,LocalDateTime now) {
        Booking b=em.find(Booking.class,id,LockModeType.PESSIMISTIC_WRITE);
        if(b==null) return;
        em.refresh(b,LockModeType.PESSIMISTIC_WRITE);
        var pickup=b.getPickupTripStop().getPlannedDepartureTime();
        boolean eligible=b.getStatus()==BookingStatus.CONFIRMED || (b.getStatus()==BookingStatus.PENDING
                && b.getSource()==BookingSource.PHONE && b.getPaymentMethod()==PaymentMethod.PAY_ON_BOARD);
        if(!eligible || pickup==null || !java.util.Set.of(TripStatus.SCHEDULED,TripStatus.BOARDING,TripStatus.DEPARTED).contains(b.getTrip().getStatus())
                || !pickup.isAfter(now.plusHours(hours).minusMinutes(15)) || pickup.isAfter(now.plusHours(hours))
                || b.getCreatedAt().isAfter(pickup.minusHours(hours))
                || b.getItems().stream().noneMatch(i->!i.isCancelled())) return;
        notifications.record(b,type,"once");
    }
}
