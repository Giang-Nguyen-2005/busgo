package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.booking.*;
import com.busgo.booking.ModificationDtos.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.payment.entity.PaymentMethod;
import com.busgo.user.entity.RoleCode;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("dev")
class M20ModificationConcurrencyIT extends M20Support {
    @Autowired TransactionTemplate tx;
    private Fixture f;
    private CurrentUser admin;
    @BeforeEach void setup() {
        f=tx.execute(s->expanded()); admin=tx.execute(s->actor(f,RoleCode.OPERATOR_ADMIN)); authenticate(admin);
    }
    private long booking(long trip,long seat) {
        return assisted.create(admin,new AssistedBookingDtos.CreateRequest(trip,f.locations().get(0).getId(),f.locations().get(2).getId(),List.of(seat),"Race","0901234567",null,PaymentMethod.PAY_ON_BOARD)).bookingId();
    }
    private List<String> race(Callable<?> a,Callable<?> b) throws Exception {
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<String>>();
            for(var action:List.of(a,b)) futures.add(pool.submit(()->{
                authenticate(admin); gate.await();
                try { action.call(); return "OK"; }
                catch(com.busgo.common.exception.BusinessException e) { return e.getCode(); }
                finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
            }));
            gate.countDown(); return List.of(futures.get(0).get(20,TimeUnit.SECONDS),futures.get(1).get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void sameTargetSeatHasExactlyOneWinner() throws Exception {
        long a=booking(f.trip().getId(),f.seats().get(0).getId()),b=booking(f.trip().getId(),f.seats().get(1).getId());
        var ar=seatRequest(a,f,3); var br=seatRequest(b,f,3);
        assertThat(race(()->modifications.create(admin,a,true,ar),()->modifications.create(admin,b,true,br))).containsExactlyInAnyOrder("OK","SEAT_NOT_AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT hold_token) FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND status='HELD'",Integer.class,f.seats().get(3).getId())).isOne();
    }
    @Test void sameBookingAllowsOnlyOneActiveAttempt() throws Exception {
        long b=booking(f.trip().getId(),f.seats().get(0).getId()); var a=seatRequest(b,f,3); var c=seatRequest(b,f,4);
        assertThat(race(()->modifications.create(admin,b,true,a),()->modifications.create(admin,b,true,c))).containsExactlyInAnyOrder("OK","MODIFICATION_ACTIVE");
    }
    @Test void oppositeTripDirectionsAcquireInSortedOrderAndComplete() throws Exception {
        var target=tx.execute(s->targetTrip(f)); long a=booking(f.trip().getId(),f.seats().get(0).getId()); long b=booking(target.getId(),targetSeats(target).get(0));
        var ar=new Request(Type.TRIP_CHANGE,target.getId(),List.of(new Selection(itemIds(a).get(0),targetSeats(target).get(1))));
        var br=new Request(Type.TRIP_CHANGE,f.trip().getId(),List.of(new Selection(itemIds(b).get(0),f.seats().get(1).getId())));
        assertThat(race(()->{var m=modifications.create(admin,a,true,ar);return modifications.confirm(admin,a,true,m.id());},()->{var m=modifications.create(admin,b,true,br);return modifications.confirm(admin,b,true,m.id());})).containsOnly("OK");
        assertThat(jdbc.queryForObject("SELECT trip_id FROM bookings WHERE id=?",Long.class,a)).isEqualTo(target.getId());
        assertThat(jdbc.queryForObject("SELECT trip_id FROM bookings WHERE id=?",Long.class,b)).isEqualTo(f.trip().getId());
    }
    @Test void expiredConfirmationAndExpiryRaceLeaveSourceIntact() throws Exception {
        long b=booking(f.trip().getId(),f.seats().get(0).getId()); var m=modifications.create(admin,b,true,seatRequest(b,f,3));
        jdbc.update("UPDATE booking_modifications SET expires_at=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1)),m.id());
        var results=race(()->modifications.expire(m.id()),()->modifications.confirm(admin,b,true,m.id()));
        assertThat(results).allMatch(r->r.equals("OK") || r.equals("MODIFICATION_CLOSED"));
        assertThat(modifications.read(admin,b,true,m.id()).status()).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT trip_seat_id FROM booking_items WHERE booking_id=?",Long.class,b)).isEqualTo(f.seats().get(0).getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND status='HELD'",Integer.class,f.seats().get(3).getId())).isZero();
    }
    @Test void injectedDatabaseFailureRollsBackMoneyInventoryTicketsAndHistory() {
        long b=booking(f.trip().getId(),f.seats().get(0).getId());
        assisted.record(admin,b,new AssistedBookingDtos.RecordPayment(PaymentMethod.PAY_ON_BOARD,null));
        var m=modifications.create(admin,b,true,seatRequest(b,f,3));
        // A scoped trigger injects failure after inventory and ticket writes, before completion.
        jdbc.execute("CREATE TRIGGER m20_fail_completion BEFORE UPDATE ON booking_modifications FOR EACH ROW BEGIN IF NEW.id="+m.id()+" AND NEW.status='COMPLETED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='M20 injected rollback'; END IF; END");
        try { assertThatThrownBy(()->modifications.confirm(admin,b,true,m.id())).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("DROP TRIGGER m20_fail_completion"); }
        assertThat(modifications.read(admin,b,true,m.id()).status()).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT trip_seat_id FROM booking_items WHERE booking_id=?",Long.class,b)).isEqualTo(f.seats().get(0).getId());
        assertThat(count("tickets",b)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND status='HELD'",Integer.class,f.seats().get(3).getId())).isEqualTo(2);
    }
    @AfterEach void cleanup() {
        long op=f.operator().getId();
        tx.executeWithoutResult(s->{
            String bookings="SELECT b.id FROM bookings b JOIN trips t ON t.id=b.trip_id JOIN operator_routes o ON o.id=t.operator_route_id WHERE o.operator_id="+op;
            jdbc.update("DELETE FROM booking_modification_items WHERE modification_id IN (SELECT id FROM booking_modifications WHERE operator_id=?)",op);
            jdbc.update("DELETE a FROM ticket_boarding a JOIN booking_items i ON i.id=a.booking_item_id WHERE i.booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM tickets WHERE booking_id IN ("+bookings+")");
            jdbc.update("DELETE r FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM payments WHERE booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM booking_modifications WHERE operator_id=?",op);
            jdbc.update("DELETE FROM booking_status_history WHERE booking_id IN ("+bookings+")");
            jdbc.update("UPDATE trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id SET v.booking_item_id=NULL,v.status='AVAILABLE' WHERE i.booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM booking_items WHERE booking_id IN ("+bookings+")");
            jdbc.update("DELETE FROM bookings WHERE id IN (SELECT id FROM ("+bookings+") x)");
            String tripIds="SELECT t.id FROM trips t JOIN operator_routes o ON o.id=t.operator_route_id WHERE o.operator_id="+op;
            jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id IN ("+tripIds+"))");
            jdbc.update("DELETE FROM trip_seats WHERE trip_id IN ("+tripIds+")");
            jdbc.update("DELETE FROM trip_segments WHERE trip_id IN ("+tripIds+")");
            jdbc.update("DELETE FROM trip_stops WHERE trip_id IN ("+tripIds+")");
            jdbc.update("DELETE FROM trips WHERE operator_route_id=?",f.operatorRoute().getId());
            jdbc.update("DELETE FROM operator_route_fares WHERE operator_route_id=?",f.operatorRoute().getId());
            jdbc.update("DELETE FROM buses WHERE id=?",f.bus().getId());
            jdbc.update("DELETE FROM seat_templates WHERE bus_type_id=?",f.busType().getId());
            jdbc.update("DELETE FROM bus_types WHERE id=?",f.busType().getId());
            jdbc.update("DELETE FROM operator_routes WHERE id=?",f.operatorRoute().getId());
            jdbc.update("DELETE FROM route_stops WHERE route_id=?",f.route().getId());
            jdbc.update("DELETE FROM routes WHERE id=?",f.route().getId());
            jdbc.update("DELETE FROM operator_staff WHERE operator_id=?",op);
            jdbc.update("DELETE FROM transport_operators WHERE id=?",op);
            for(var location:f.locations()) jdbc.update("DELETE FROM locations WHERE id=?",location.getId());
            jdbc.update("DELETE FROM user_roles WHERE user_id=?",admin.id());
            jdbc.update("DELETE FROM users WHERE id=?",admin.id());
        });
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
