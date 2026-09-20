package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;

import com.busgo.booking.*;
import com.busgo.hold.SeatHoldDtos.SeatHoldResponse;
import com.busgo.payment.PaymentTicketDtos.PaymentConfirmation;
import com.busgo.payment.PaymentTicketService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses separate committed MySQL transactions to exercise the booking row lock. */
@SpringBootTest
@ActiveProfiles("dev")
class M9PaymentConcurrencyIT extends M8BookingTestSupport {
    @Autowired BookingService bookingService;
    @Autowired PaymentTicketService paymentTickets;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    private Fixture fixture;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanOwnedFixture() {
        if (fixture != null) {
            Long tripId = fixture.trip().getId();
            jdbc.update("DELETE ticket FROM tickets ticket JOIN bookings booking ON booking.id=ticket.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE history FROM booking_status_history history JOIN bookings booking ON booking.id=history.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE payment FROM payments payment JOIN bookings booking ON booking.id=payment.booking_id WHERE booking.trip_id=?", tripId);
            jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id=?)", tripId);
            jdbc.update("DELETE item FROM booking_items item JOIN bookings booking ON booking.id=item.booking_id WHERE booking.trip_id=?", tripId);
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
            jdbc.update("DELETE FROM transport_operators WHERE id=?", fixture.operator().getId());
            for (var location : fixture.locations()) jdbc.update("DELETE FROM locations WHERE id=?", location.getId());
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users WHERE id=?", userId);
        }
    }

    @Test
    void simultaneousConfirmationsReturnOneCommittedPaymentAndTicketSet() throws Exception {
        fixture = transactions.execute(status -> fixture());
        UserAuth owner = transactions.execute(status -> customer("m9-race"));
        userIds.add(Objects.requireNonNull(owner).user().id());
        SeatHoldResponse held = transactions.execute(status -> hold(owner, fixture, 0, 2, 0, 1));
        BookingDtos.CreateBookingRequest request = new BookingDtos.CreateBookingRequest(
                Objects.requireNonNull(held).holdToken(), "Nguyen Giang", "0901234567",
                "giang@example.test");
        BookingDtos.BookingResponse booking = transactions.execute(
                status -> bookingService.create(owner.user(), request));
        long bookingId = Objects.requireNonNull(booking).bookingId();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<PaymentConfirmation> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return paymentTickets.confirm(owner.user(), bookingId);
            };
            Future<PaymentConfirmation> first = executor.submit(attempt);
            Future<PaymentConfirmation> second = executor.submit(attempt);
            PaymentConfirmation a = first.get(20, TimeUnit.SECONDS);
            PaymentConfirmation b = second.get(20, TimeUnit.SECONDS);
            assertThat(a.paymentId()).isEqualTo(b.paymentId());
            assertThat(a.transactionReference()).isEqualTo(b.transactionReference());
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE booking_id=?",
                Integer.class, bookingId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickets WHERE booking_id=?",
                Integer.class, bookingId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT booking_item_id) FROM tickets WHERE booking_id=?",
                Integer.class, bookingId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_status_history WHERE booking_id=?",
                Integer.class, bookingId)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM bookings WHERE id=?",
                String.class, bookingId)).isEqualTo("CONFIRMED");
    }
}
