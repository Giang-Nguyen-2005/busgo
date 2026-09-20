package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;

import com.busgo.booking.*;
import com.busgo.common.exception.BusinessException;
import com.busgo.hold.SeatHoldDtos.SeatHoldResponse;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses separate committed MySQL transactions to exercise SELECT ... FOR UPDATE. */
@SpringBootTest
@ActiveProfiles("dev")
class M8BookingConcurrencyIT extends M8BookingTestSupport {
    @Autowired BookingService bookings;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    private Fixture fixture;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanOwnedFixture() {
        if (fixture != null) {
            Long tripId = fixture.trip().getId();
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
    void sameLogicalHoldBookedConcurrentlyHasExactlyOneWinnerAndConsistentInventory()
            throws Exception {
        fixture = transactions.execute(status -> fixture());
        UserAuth owner = transactions.execute(status -> customer("m8-race"));
        userIds.add(Objects.requireNonNull(owner).user().id());
        SeatHoldResponse held = transactions.execute(status -> hold(owner, fixture, 0, 2, 0, 1));
        var request = new BookingDtos.CreateBookingRequest(Objects.requireNonNull(held).holdToken(),
                "Nguyen Giang", "0901234567", "giang@example.test");

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Result> first = executor.submit(attempt(barrier, owner, request));
            Future<Result> second = executor.submit(attempt(barrier, owner, request));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(Result.SUCCESS, Result.HOLD_NOT_FOUND);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bookings WHERE trip_id=?",
                Integer.class, fixture.trip().getId())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM booking_items item JOIN bookings booking ON booking.id=item.booking_id
                WHERE booking.trip_id=?
                """, Integer.class, fixture.trip().getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_seat_segment_inventory inventory
                JOIN trip_seats seat ON seat.id=inventory.trip_seat_id
                WHERE seat.trip_id=? AND inventory.status='BOOKED'
                  AND inventory.booking_item_id IS NOT NULL AND inventory.hold_token IS NULL
                  AND inventory.held_by_user_id IS NULL AND inventory.hold_expires_at IS NULL
                """, Integer.class, fixture.trip().getId())).isEqualTo(4);
    }

    private Callable<Result> attempt(CyclicBarrier barrier, UserAuth owner,
            BookingDtos.CreateBookingRequest request) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                bookings.create(owner.user(), request);
                return Result.SUCCESS;
            } catch (BusinessException ex) {
                assertThat(ex.getCode()).isEqualTo("SEAT_HOLD_NOT_FOUND");
                return Result.HOLD_NOT_FOUND;
            }
        };
    }

    private enum Result { SUCCESS, HOLD_NOT_FOUND }
}
