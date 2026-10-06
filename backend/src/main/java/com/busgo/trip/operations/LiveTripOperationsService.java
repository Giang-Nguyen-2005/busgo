package com.busgo.trip.operations;

import static com.busgo.common.time.BusGoTime.*;
import static com.busgo.common.time.JpaJdbcTime.*;
import com.busgo.booking.entity.Booking;
import com.busgo.common.exception.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.notification.*;
import com.busgo.operator.OperatorContextService;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.TripRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class LiveTripOperationsService {
    public record Update(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,100}") String requestKey,
            @NotNull @Min(0) Integer delayMinutes, @Size(max=500) String reason,
            OffsetDateTime expectedArrivalAt) {}
    public record History(long id, String updateType, LiveTripState state, OffsetDateTime createdAt) {}
    private final TripRepository trips;
    private final OperatorContextService context;
    private final JdbcTemplate db;
    private final Clock clock;
    private final NotificationService notifications;
    private final EntityManager em;
    public LiveTripOperationsService(TripRepository trips,OperatorContextService context,JdbcTemplate db,
            Clock clock,NotificationService notifications,EntityManager em) {
        this.trips=trips;this.context=context;this.db=db;this.clock=clock;this.notifications=notifications;this.em=em;
    }
    private Trip owned(CurrentUser user,long id,boolean write) {
        long oid=(write?context.requireAdminOperator(user):context.requireOperatorMember(user)).getId();
        return (write?trips.lockOwnedById(id,oid):trips.findOwnedById(id,oid))
            .orElseThrow(()->new ResourceNotFoundException("TRIP_NOT_FOUND","Trip was not found."));
    }
    @Transactional(readOnly=true)
    public LiveTripState current(CurrentUser user,long id) { return LiveTripState.of(owned(user,id,false),null); }
    public LiveTripState update(CurrentUser user,long id,Update input,boolean eta) {
        Trip t=owned(user,id,true);
        if(input.requestKey()==null || !input.requestKey().matches("[A-Za-z0-9_-]{1,100}")
                || input.delayMinutes()==null || input.delayMinutes()<0) throw invalid("Invalid request key or delay.");
        String reason=input.reason()==null || input.reason().isBlank()?null:input.reason().strip();
        if(reason!=null && (reason.length()>500 || reason.codePoints().anyMatch(Character::isISOControl))) throw invalid("Invalid reason.");
        if(!eta && input.expectedArrivalAt()!=null) throw invalid("Use ETA action for an explicit arrival estimate.");
        LocalDateTime arrival=input.expectedArrivalAt()==null?null:utc(input.expectedArrivalAt().toInstant());
        String payload=eta+"|"+input.delayMinutes()+"|"+Objects.toString(arrival,"")+"|"+Objects.toString(reason,"");
        var previous=db.queryForList("SELECT request_payload FROM trip_operational_updates WHERE trip_id=? AND request_key=?",String.class,id,input.requestKey());
        if(!previous.isEmpty()) {
            if(!previous.get(0).equals(payload)) throw new BusinessException("OPERATION_REQUEST_CONFLICT","Request key was already used with different content.",HttpStatus.CONFLICT,null);
            return LiveTripState.of(t,null);
        }
        if(t.getStatus()==TripStatus.COMPLETED || t.getStatus()==TripStatus.CANCELLED) throw invalid("Terminal trip cannot receive operational updates.");
        if(eta && (t.getStatus()!=TripStatus.DEPARTED || arrival==null)) throw invalid("ETA requires a departed trip and expected arrival.");
        t.setDelayMinutes(input.delayMinutes());
        t.setDelayReason(reason);
        if(t.getStatus()!=TripStatus.DEPARTED) t.setExpectedDepartureAt(t.getDepartureTime().plusMinutes(input.delayMinutes()));
        t.setExpectedArrivalAt(arrival==null?t.getEstimatedArrivalTime().plusMinutes(input.delayMinutes()):arrival);
        LocalDateTime departure=t.getActualDepartureAt()!=null?t.getActualDepartureAt()
            :t.getExpectedDepartureAt()!=null?t.getExpectedDepartureAt():t.getDepartureTime();
        if(!t.getExpectedArrivalAt().isAfter(departure)
                || (t.getExpectedDepartureAt()!=null && !t.getExpectedArrivalAt().isAfter(t.getExpectedDepartureAt()))) throw invalid("Expected arrival must follow departure.");
        t.setOperationalUpdatedAt(utc(clock.instant()));
        trips.flush();
        append(t,user,input.requestKey(),payload,eta?"ETA_UPDATED":input.delayMinutes()==0?"DELAY_CLEARED":"DELAY_UPDATED",
            eta && input.delayMinutes()==0?NotificationType.TRIP_ETA_UPDATED:input.delayMinutes()==0?NotificationType.TRIP_DELAY_CLEARED:NotificationType.TRIP_DELAYED);
        return LiveTripState.of(t,null);
    }
    /** Joins the existing forward lifecycle transaction and its trip lock. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void transitioned(Trip t,CurrentUser user) {
        var now=utc(clock.instant());
        if(t.getStatus()==TripStatus.DEPARTED && t.getActualDepartureAt()==null) t.setActualDepartureAt(now);
        if(t.getStatus()==TripStatus.COMPLETED && t.getActualArrivalAt()==null) {
            if(t.getActualDepartureAt()!=null && now.isBefore(t.getActualDepartureAt())) throw invalid("Arrival cannot precede departure.");
            t.setActualArrivalAt(now);
        }
        t.setOperationalUpdatedAt(now);trips.flush();
        append(t,user,":lifecycle:"+t.getStatus(),t.getStatus().name(),t.getStatus().name(),
            t.getStatus()==TripStatus.DEPARTED?NotificationType.TRIP_DEPARTED:NotificationType.TRIP_COMPLETED);
    }
    private void append(Trip t,CurrentUser user,String key,String payload,String type,NotificationType notification) {
        long oid=t.getOperatorRoute().getOperator().getId();
        db.update("""
            INSERT INTO trip_operational_updates(trip_id,operator_id,actor_id,request_key,request_payload,lifecycle,update_type,delay_minutes,reason,
            expected_departure_at,expected_arrival_at,actual_departure_at,actual_arrival_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,t.getId(),oid,user.id(),key,payload,t.getStatus().name(),type,t.getDelayMinutes(),t.getDelayReason(),
            parameter(t.getExpectedDepartureAt()),parameter(t.getExpectedArrivalAt()),parameter(t.getActualDepartureAt()),parameter(t.getActualArrivalAt()),parameter(t.getOperationalUpdatedAt()));
        long occurrence=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        // Preserve commercial write ordering: trip -> operator -> bookings in ID order.
        db.queryForObject("SELECT id FROM transport_operators WHERE id=? FOR UPDATE",Long.class,oid);
        var ids=db.queryForList("""
            SELECT b.id FROM bookings b WHERE b.trip_id=?
            AND (b.status='CONFIRMED' OR (b.status='PENDING' AND b.source='PHONE' AND b.payment_method='PAY_ON_BOARD'))
            AND EXISTS(SELECT 1 FROM booking_items i WHERE i.booking_id=b.id AND i.cancelled=FALSE)
            ORDER BY b.id FOR UPDATE
            """,Long.class,t.getId());
        for(long id:ids) notifications.record(em.find(Booking.class,id),notification,"operation_"+occurrence);
    }
    @Transactional(readOnly=true)
    public List<History> history(CurrentUser user,long id) {
        Trip t=owned(user,id,false);
        return db.query("SELECT * FROM trip_operational_updates WHERE trip_id=? ORDER BY id DESC LIMIT 50",(r,n)-> {
            var status=TripStatus.valueOf(r.getString("lifecycle"));int delay=r.getInt("delay_minutes");
            return new History(r.getLong("id"),r.getString("update_type"),new LiveTripState(status,delay,r.getString("reason"),
                api(t.getDepartureTime()),api(t.getEstimatedArrivalTime()),api(read(r,"expected_departure_at")),api(read(r,"expected_arrival_at")),null,null,
                api(read(r,"actual_departure_at")),api(read(r,"actual_arrival_at")),api(read(r,"created_at")),LiveTripState.label(status,delay)),api(read(r,"created_at")));
        },id);
    }
    private static BusinessException invalid(String message) { return new BusinessException("INVALID_OPERATIONAL_UPDATE",message,HttpStatus.BAD_REQUEST,null); }
}
