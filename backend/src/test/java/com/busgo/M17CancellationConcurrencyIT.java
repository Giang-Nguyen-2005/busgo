package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.booking.*;
import com.busgo.booking.AssistedBookingDtos.*;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
@ActiveProfiles("dev")
class M17CancellationConcurrencyIT extends M16ATestSupport {
    @Autowired TransactionTemplate transactions;
    @Autowired CancellationService cancellations;
    @Autowired com.busgo.operations.OperationsService ops;
    @Autowired com.busgo.trip.operations.OperatorTripOperationsService lifecycle;
    private Fixture fixture;
    private CurrentUser admin;
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> foreignOperatorIds = new ArrayList<>();
    @BeforeEach void setup() {
        fixture = transactions.execute(s -> fixture());
        admin = transactions.execute(s -> actor(fixture, RoleCode.OPERATOR_ADMIN));
        userIds.add(admin.id()); authenticate(admin);
    }
    @Override void authenticate(CurrentUser actor) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,
                null, actor.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList()));
    }
    @AfterEach
    void cleanOwnedFixture() {
        if (fixture != null) {
            Long tripId = fixture.trip().getId();
            jdbc.update("DELETE tb FROM ticket_boarding tb JOIN tickets tk ON tk.id=tb.ticket_id JOIN bookings b ON b.id=tk.booking_id WHERE b.trip_id=?", tripId);
            M16BFixtures.clear(jdbc,fixture.operator().getId(),tripId);
            jdbc.update("DELETE r FROM refunds r JOIN payments p ON p.id=r.payment_id JOIN bookings b ON b.id=p.booking_id WHERE b.trip_id=?",tripId);
            jdbc.update("DELETE ticket FROM tickets ticket JOIN bookings booking ON booking.id=ticket.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE history FROM booking_status_history history JOIN bookings booking ON booking.id=history.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE payment FROM payments payment JOIN bookings booking ON booking.id=payment.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id=?)", tripId);
            jdbc.update("DELETE item FROM booking_items item JOIN bookings booking ON booking.id=item.booking_id WHERE booking.trip_id=?", tripId);
            NotificationTestCleanup.bookings(jdbc,"SELECT id FROM bookings WHERE trip_id="+tripId);
            jdbc.update("DELETE FROM bookings WHERE trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_seats WHERE trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_segments WHERE trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_stops WHERE trip_id=?", tripId);
            jdbc.update("DELETE FROM trips WHERE id=?", tripId);
            jdbc.update("DELETE FROM operator_route_fares WHERE operator_route_id=?", fixture.operatorRoute().getId());
            jdbc.update("DELETE FROM buses WHERE id=?", fixture.bus().getId());
            jdbc.update("DELETE FROM seat_templates WHERE bus_type_id=?", fixture.busType().getId());
            jdbc.update("DELETE FROM bus_types WHERE id=?", fixture.busType().getId());
            jdbc.update("DELETE FROM operator_routes WHERE id=?", fixture.operatorRoute().getId());
            jdbc.update("DELETE FROM route_stops WHERE route_id=?", fixture.route().getId());
            jdbc.update("DELETE FROM routes WHERE id=?", fixture.route().getId());
            jdbc.update("DELETE FROM operator_staff WHERE operator_id=?", fixture.operator().getId());
            jdbc.update("DELETE FROM transport_operators WHERE id=?", fixture.operator().getId());
            for (var location : fixture.locations()) jdbc.update("DELETE FROM locations WHERE id=?", location.getId());
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM operator_staff WHERE user_id=?",userId);
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users WHERE id=?", userId);
        }
        for(Long operatorId:foreignOperatorIds) jdbc.update("DELETE FROM transport_operators WHERE id=?",operatorId);
    }


    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }


    private final CancellationDtos.Request reason=new CancellationDtos.Request("race");
    private long reservation(com.busgo.payment.entity.PaymentMethod method) {
        return assisted.create(admin,request(fixture,0,2,method)).bookingId();
    }
    private String attempt(Callable<?> action) {
        authenticate(admin);
        try { action.call(); return "SUCCESS"; }
        catch(com.busgo.common.exception.BusinessException e) { return e.getCode(); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
    private String cancel(long id) { return attempt(()->cancellations.operatorCancel(admin,id,reason)); }
    private String pay(long id,PaymentMethod method) { return attempt(()->assisted.record(admin,id,new RecordPayment(method,null))); }
    private List<String> race(Callable<String> first,Callable<String> second) throws Exception {
        CyclicBarrier barrier=new CyclicBarrier(2); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<String> a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);try{return first.call();}finally{SecurityContextHolder.clearContext();}});
            Future<String> b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);try{return second.call();}finally{SecurityContextHolder.clearContext();}});
            return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    private void finalCancellation(long id,boolean paid) {
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id)).isEqualTo("CANCELLED");
        assertThat(count("payments",id)).isEqualTo(paid?1:0); assertThat(count("tickets",id)).isEqualTo(paid?1:0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds r JOIN payments p ON p.id=r.payment_id WHERE p.booking_id=?",Integer.class,id)).isEqualTo(paid?1:0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE booking_id=? AND status='VALID'",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id WHERE i.booking_id=?",Integer.class,id)).isZero();
    }
    @Test void paymentVsUnpaidCancellationHasOneConsistentFinalState() throws Exception {
        long id=reservation(PaymentMethod.PAY_ON_BOARD);
        var outcomes=race(()->pay(id,PaymentMethod.PAY_ON_BOARD),()->cancel(id));
        assertThat(outcomes.get(1)).isEqualTo("SUCCESS");
        assertThat(outcomes.get(0)).isIn("SUCCESS","BOOKING_NOT_PAYABLE");
        finalCancellation(id,outcomes.get(0).equals("SUCCESS"));
    }
    @Test void duplicateUnpaidCancellationReleasesExactlyOnce() throws Exception {
        long id=reservation(PaymentMethod.PAY_ON_BOARD);
        var before=jdbc.queryForList("SELECT version FROM trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id WHERE i.booking_id=? ORDER BY v.id",Long.class,id);
        assertThat(race(()->cancel(id),()->cancel(id))).containsOnly("SUCCESS");
        finalCancellation(id,false); assertThat(count("booking_status_history",id)).isOne();
        var versions=jdbc.queryForList("SELECT version FROM trip_seat_segment_inventory WHERE trip_seat_id=? AND trip_segment_id IN (?,?) ORDER BY id",Long.class,fixture.seats().get(0).getId(),fixture.segments().get(0).getId(),fixture.segments().get(1).getId());
        for(int n=0;n<versions.size();n++) assertThat(versions.get(n)).isEqualTo(before.get(n)+1);
    }
    @Test void duplicatePaidCancellationCreatesOneRefundAndHistory() throws Exception {
        long id=reservation(PaymentMethod.PAY_ON_BOARD); pay(id,PaymentMethod.PAY_ON_BOARD);
        assertThat(race(()->cancel(id),()->cancel(id))).containsOnly("SUCCESS");
        finalCancellation(id,true); assertThat(count("booking_status_history",id)).isEqualTo(2);
    }
    @Test void publicPaymentVsCancellationCannotResurrectBooking() throws Exception {
        long id=reservation(PaymentMethod.QR_TRANSFER); String token=token(assisted.issueLink(admin,id));
        var outcomes=race(()->attempt(()->assisted.publicConfirm(token)),()->cancel(id));
        assertThat(outcomes.get(0)).isIn("SUCCESS","BOOKING_NOT_PAYABLE");
        assertThat(outcomes.get(1)).isEqualTo("SUCCESS"); finalCancellation(id,outcomes.get(0).equals("SUCCESS"));
    }
    @Test void expiryVsLatePaymentProducesCancelledUnpaidState() throws Exception {
        long id=reservation(PaymentMethod.QR_TRANSFER);
        jdbc.update("UPDATE bookings SET payment_due_at=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1)),id);
        var outcomes=race(()->pay(id,PaymentMethod.QR_TRANSFER),()->attempt(()->assertThat(cancellations.expire(id)).isTrue()));
        assertThat(outcomes.get(0)).isIn("PAYMENT_WINDOW_CLOSED","BOOKING_NOT_PAYABLE");
        assertThat(outcomes.get(1)).isEqualTo("SUCCESS"); finalCancellation(id,false);
    }
    @Test void committedPaymentBeatsWaitingExpiry() throws Exception {
        long id=reservation(PaymentMethod.QR_TRANSFER);
        CountDownLatch paid=new CountDownLatch(1),expiryStarted=new CountDownLatch(1); ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var payment=pool.submit(()->transactions.execute(s->{
                assertThat(pay(id,PaymentMethod.QR_TRANSFER)).isEqualTo("SUCCESS");
                jdbc.update("UPDATE bookings SET payment_due_at=? WHERE id=?",com.busgo.common.time.JpaJdbcTime.parameter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusSeconds(1)),id);
                paid.countDown(); try { assertThat(expiryStarted.await(10,TimeUnit.SECONDS)).isTrue(); } catch(InterruptedException e){throw new RuntimeException(e);} return "PAID";
            }));
            var expiry=pool.submit(()->{assertThat(paid.await(10,TimeUnit.SECONDS)).isTrue();expiryStarted.countDown();return cancellations.expire(id);});
            assertThat(payment.get(30,TimeUnit.SECONDS)).isEqualTo("PAID"); assertThat(expiry.get(30,TimeUnit.SECONDS)).isFalse();
            assertThat(count("payments",id)).isOne(); assertThat(count("tickets",id)).isOne();
            assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",String.class,id)).isEqualTo("CONFIRMED");
        } finally {pool.shutdownNow();}
    }
    private long paidBoardingBooking() {
        long id=reservation(PaymentMethod.PAY_ON_BOARD); pay(id,PaymentMethod.PAY_ON_BOARD);
        transactions.executeWithoutResult(s->M16BFixtures.crew(jdbc,fixture.operator().getId(),fixture.trip().getId(),admin.id()));
        lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.BOARDING);
        return id;
    }
    @Test void cancellationVsCheckInSerializes() throws Exception { attendanceRace("check-in"); }
    @Test void cancellationVsBoardingSerializes() throws Exception { attendanceRace("direct-board"); }
    @Test void cancellationVsNoShowSerializes() throws Exception { attendanceRace("no-show"); }
    private void attendanceRace(String command) throws Exception {
        long id=paidBoardingBooking(); long ticket=jdbc.queryForObject("SELECT id FROM tickets WHERE booking_id=?",Long.class,id);
        var outcomes=race(()->cancel(id),()->attempt(()->ops.transition(admin,fixture.trip().getId(),ticket,command,new com.busgo.operations.OperationsDtos.PickupContext(fixture.stops().get(0).getId(),null))));
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS",outcomes.get(0).equals("SUCCESS")?"TICKET_NOT_ELIGIBLE":"CANCELLATION_ATTENDANCE_CONFLICT");
        if(outcomes.get(0).equals("SUCCESS")) finalCancellation(id,true);
        else assertThat(jdbc.queryForObject("SELECT status FROM tickets WHERE id=?",String.class,ticket)).isEqualTo("VALID");
    }
    @Test void cancellationVsTicketlessNoShowSerializes() throws Exception {
        long id=reservation(PaymentMethod.PAY_ON_BOARD);
        transactions.executeWithoutResult(s->M16BFixtures.crew(jdbc,fixture.operator().getId(),fixture.trip().getId(),admin.id()));
        lifecycle.updateStatus(admin,fixture.trip().getId(),com.busgo.trip.entity.TripStatus.BOARDING);
        long item=jdbc.queryForObject("SELECT id FROM booking_items WHERE booking_id=?",Long.class,id);
        var outcomes=race(()->cancel(id),()->attempt(()->ops.reservationNoShow(admin,fixture.trip().getId(),item,new com.busgo.operations.OperationsDtos.PickupContext(fixture.stops().get(0).getId(),null))));
        assertThat(outcomes).containsExactlyInAnyOrder("SUCCESS",outcomes.get(0).equals("SUCCESS")?"RESERVATION_NOT_ELIGIBLE":"CANCELLATION_ATTENDANCE_CONFLICT");
    }
    @Test void cancellationVsPickupClosureRetainsOperationalIntegrity() throws Exception {
        long id=paidBoardingBooking();
        var outcomes=race(()->cancel(id),()->attempt(()->ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(0).getId(),null)));
        assertThat(outcomes.get(0)).isEqualTo("SUCCESS"); assertThat(outcomes.get(1)).isIn("SUCCESS","PICKUP_UNRESOLVED");
        finalCancellation(id,true);
        if(outcomes.get(1).equals("PICKUP_UNRESOLVED")) ops.closePickup(admin,fixture.trip().getId(),fixture.stops().get(0).getId(),null);
    }
    @Test void releaseThenNewCustomerHoldsSameSeatAndNonOverlapSurvives() throws Exception {
        long id=reservation(PaymentMethod.PAY_ON_BOARD);
        long other=assisted.create(admin,request(fixture,2,3,PaymentMethod.PAY_ON_BOARD)).bookingId();
        UserAuth owner=transactions.execute(s->customer("m17-reuse")); userIds.add(owner.user().id());
        var outcomes=race(()->cancel(id),()->{
            try {hold(owner,fixture,0,2,0);return "HELD";} catch(com.busgo.common.exception.BusinessException e){return e.getCode();}
        });
        assertThat(outcomes.get(0)).isEqualTo("SUCCESS"); assertThat(outcomes.get(1)).isIn("HELD","SEAT_NOT_AVAILABLE");
        if(!outcomes.get(1).equals("HELD")) assertThat(hold(owner,fixture,0,2,0).status().name()).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory v JOIN booking_items i ON i.id=v.booking_item_id WHERE i.booking_id=? AND v.status='BOOKED'",Integer.class,other)).isOne();
    }
    @Test void foreignCustomerRacingOwnerCancellationSeesOnlyNotFound() throws Exception {
        long id=reservation(PaymentMethod.PAY_ON_BOARD); UserAuth foreign=transactions.execute(s->customer("m17-foreign-race")); userIds.add(foreign.user().id());
        var outcomes=race(()->cancel(id),()->{
            authenticate(foreign.user());
            try {cancellations.customerCancel(foreign.user(),id,reason);return "LEAK";}
            catch(com.busgo.common.exception.ResourceNotFoundException e){return "NOT_FOUND";}
        }); assertThat(outcomes).containsExactly("SUCCESS","NOT_FOUND"); finalCancellation(id,false);
    }
    @Test void foreignOperatorRacingOwnerCancellationSeesOnlyNotFound() throws Exception {
        long id=reservation(PaymentMethod.PAY_ON_BOARD);
        var foreign=transactions.execute(s->actor(fixture,RoleCode.OPERATOR_ADMIN));userIds.add(foreign.id());
        long foreignOperator=transactions.execute(s->{
            var op=new com.busgo.operator.entity.TransportOperator();op.setCode(UUID.randomUUID().toString());op.setName("Foreign M17");op.setStatus(com.busgo.operator.entity.OperatorStatus.ACTIVE);return operators.saveAndFlush(op).getId();
        });foreignOperatorIds.add(foreignOperator);
        jdbc.update("UPDATE operator_staff SET operator_id=? WHERE user_id=?",foreignOperator,foreign.id());
        var outcomes=race(()->cancel(id),()->{
            authenticate(foreign);
            try{cancellations.operatorCancel(foreign,id,reason);return "LEAK";}
            catch(com.busgo.common.exception.ResourceNotFoundException e){return "NOT_FOUND";}
        });assertThat(outcomes).containsExactly("SUCCESS","NOT_FOUND");finalCancellation(id,false);
    }
    @Test void paymentCommittedFirstIsRefundedByWaitingCancellation() throws Exception { orderedPaymentCancellation(true); }
    @Test void cancellationCommittedFirstRejectsWaitingPayment() throws Exception { orderedPaymentCancellation(false); }
    private void orderedPaymentCancellation(boolean paymentFirst) throws Exception {
        long id=reservation(PaymentMethod.PAY_ON_BOARD);CountDownLatch firstWritten=new CountDownLatch(1),secondStarted=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->transactions.execute(s->{
                String result=paymentFirst?pay(id,PaymentMethod.PAY_ON_BOARD):cancel(id);
                firstWritten.countDown();try{assertThat(secondStarted.await(10,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){throw new RuntimeException(e);}return result;
            }));
            var second=pool.submit(()->{assertThat(firstWritten.await(10,TimeUnit.SECONDS)).isTrue();secondStarted.countDown();return paymentFirst?cancel(id):pay(id,PaymentMethod.PAY_ON_BOARD);});
            assertThat(first.get(30,TimeUnit.SECONDS)).isEqualTo("SUCCESS");
            assertThat(second.get(30,TimeUnit.SECONDS)).isEqualTo(paymentFirst?"SUCCESS":"BOOKING_NOT_PAYABLE");finalCancellation(id,paymentFirst);
        } finally {pool.shutdownNow();}
    }
}
