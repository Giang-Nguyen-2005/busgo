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
class M16AConcurrencyIT extends M16ATestSupport {
    @Autowired TransactionTemplate transactions;
    private Fixture fixture;
    private CurrentUser admin;
    private final List<Long> userIds = new ArrayList<>();
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
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users WHERE id=?", userId);
        }
    }


    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void twoOperatorsHaveExactlyOneWinnerForAnOverlappingSeat() throws Exception {
        CurrentUser second = transactions.execute(s -> actor(fixture, RoleCode.OPERATOR_ADMIN)); userIds.add(second.id());
        var outcomes = race(() -> attemptPhone(admin), () -> attemptPhone(second));
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "SEAT_NOT_AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE trip_id=?", Integer.class, fixture.trip().getId())).isOne();
    }
    @Test void customerWebConversionAndPhoneBookingRaceSafely() throws Exception {
        UserAuth customer = transactions.execute(s -> customer("m16-web-race")); userIds.add(customer.user().id());
        var outcomes = race(() -> {
            try {
                var held = holds.create(customer.user(), new com.busgo.hold.SeatHoldDtos.CreateSeatHoldRequest(fixture.trip().getId(), fixture.locations().get(0).getId(), fixture.locations().get(2).getId(), List.of(fixture.seats().get(0).getId())));
                bookingService.create(customer.user(), new BookingDtos.CreateBookingRequest(held.holdToken(), "Web", "0901234567", "web@example.test"));
                return "CREATED";
            } catch (com.busgo.common.exception.BusinessException e) { return e.getCode(); }
        }, () -> attemptPhone(admin));
        assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "SEAT_NOT_AVAILABLE");
    }
    @Test void manualAndPublicConfirmationRaceToOnePaymentTicketAndHistory() throws Exception {
        var booking = assisted.create(admin, request(fixture, 0, 2, PaymentMethod.QR_TRANSFER));
        String token = token(assisted.issueLink(admin, booking.bookingId()));
        var outcomes = race(() -> {
            authenticate(admin); assisted.record(admin, booking.bookingId(), new RecordPayment(PaymentMethod.QR_TRANSFER, "Confirmed manually")); return "PAID";
        }, () -> { assisted.publicConfirm(token); return "PAID"; });
        assertThat(outcomes).containsExactly("PAID", "PAID");
        assertThat(count("payments", booking.bookingId())).isOne(); assertThat(count("tickets", booking.bookingId())).isOne();
        assertThat(count("booking_status_history", booking.bookingId())).isOne();
        assertThat(assisted.publicConfirm(token).status().name()).isEqualTo("CONFIRMED");
        assertThat(count("tickets", booking.bookingId())).isOne();
    }
    @Test void duplicateManualConfirmationAndNonOverlappingReuseRemainSafe() throws Exception {
        var booking = assisted.create(admin, request(fixture, 0, 2, PaymentMethod.PAY_ON_BOARD));
        var second = assisted.create(admin, request(fixture, 2, 3, PaymentMethod.PAY_ON_BOARD));
        assertThat(second.bookingId()).isNotEqualTo(booking.bookingId());
        Callable<String> confirm = () -> { authenticate(admin); return assisted.record(admin, booking.bookingId(), new RecordPayment(PaymentMethod.PAY_ON_BOARD, null)).paymentId().toString(); };
        var outcomes = race(confirm, confirm); assertThat(outcomes.get(0)).isEqualTo(outcomes.get(1));
        assertThat(count("payments", booking.bookingId())).isOne(); assertThat(count("tickets", booking.bookingId())).isOne();
    }
    private String attemptPhone(CurrentUser actor) {
        authenticate(actor);
        try { assisted.create(actor, request(fixture, 0, 2, PaymentMethod.PAY_ON_BOARD)); return "CREATED"; }
        catch (com.busgo.common.exception.BusinessException e) { return e.getCode(); }
    }
    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2); ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); try { return first.call(); } finally { SecurityContextHolder.clearContext(); } });
            Future<String> b = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); try { return second.call(); } finally { SecurityContextHolder.clearContext(); } });
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
}
