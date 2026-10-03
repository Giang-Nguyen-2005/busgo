package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;

import com.busgo.booking.*;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.hold.*;
import com.busgo.operator.entity.OperatorStaff;
import com.busgo.operator.repository.OperatorStaffRepository;
import com.busgo.payment.PaymentTicketService;
import com.busgo.trip.entity.TripStatus;
import com.busgo.trip.operations.OperatorTripOperationsService;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Verifies that trip-first locking serializes customer mutations with operations. */
@SpringBootTest
@ActiveProfiles("dev")
class M12OperationsConcurrencyIT extends M8BookingTestSupport {
    @Autowired SeatHoldService seatHolds;
    @Autowired BookingService bookingService;
    @Autowired PaymentTicketService paymentService;
    @Autowired OperatorTripOperationsService operations;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired OperatorStaffRepository staff;

    private Fixture fixture;
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanOwnedFixture() {
        if (fixture != null) {
            Long tripId = fixture.trip().getId();
            M16BFixtures.clear(jdbc, fixture.operator().getId(), tripId);
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

    @Test
    void boardingTransitionSerializesWithHoldCreation() throws Exception {
        fixture = transactions.execute(status -> fixture());
        UserAuth customer = transactions.execute(status -> customer("m12-hold-race"));
        userIds.add(Objects.requireNonNull(customer).user().id());
        CurrentUser admin = transactions.execute(status -> admin(fixture));

        var request = new SeatHoldDtos.CreateSeatHoldRequest(fixture.trip().getId(),
                fixture.locations().get(0).getId(), fixture.locations().get(1).getId(),
                List.of(fixture.seats().get(0).getId()));
        List<String> outcomes = race(
                () -> attempt(() -> seatHolds.create(customer.user(), request)),
                () -> attemptAdmin(admin, () -> operations.updateStatus(admin, fixture.trip().getId(),
                        TripStatus.BOARDING)));
        assertThat(outcomes).contains("SUCCESS");
        assertThat(outcomes).allMatch(value -> value.equals("SUCCESS")
                || value.equals("TRIP_NOT_BOOKABLE"));
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id=?", String.class,
                fixture.trip().getId())).isEqualTo("BOARDING");
    }

    @Test
    void boardingTransitionSerializesWithBookingCreation() throws Exception {
        fixture = transactions.execute(status -> fixture());
        UserAuth customer = transactions.execute(status -> customer("m12-book-race"));
        userIds.add(Objects.requireNonNull(customer).user().id());
        CurrentUser admin = transactions.execute(status -> admin(fixture));
        var held = transactions.execute(status -> hold(customer, fixture, 0, 1, 0));
        var request = new BookingDtos.CreateBookingRequest(Objects.requireNonNull(held).holdToken(),
                "Race Passenger", "0901234567", "race@example.test");

        List<String> outcomes = race(
                () -> attempt(() -> bookingService.create(customer.user(), request)),
                () -> attemptAdmin(admin, () -> operations.updateStatus(admin, fixture.trip().getId(),
                        TripStatus.BOARDING)));
        assertThat(outcomes).contains("SUCCESS");
        assertThat(outcomes).allMatch(value -> value.equals("SUCCESS")
                || value.equals("TRIP_NOT_BOOKABLE"));
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id=?", String.class,
                fixture.trip().getId())).isEqualTo("BOARDING");
    }

    @Test
    void departureTransitionSerializesWithPaymentConfirmation() throws Exception {
        fixture = transactions.execute(status -> fixture());
        UserAuth customer = transactions.execute(status -> customer("m12-pay-race"));
        userIds.add(Objects.requireNonNull(customer).user().id());
        CurrentUser admin = transactions.execute(status -> admin(fixture));
        var held = transactions.execute(status -> hold(customer, fixture, 0, 1, 0));
        var request = new BookingDtos.CreateBookingRequest(Objects.requireNonNull(held).holdToken(),
                "Race Passenger", "0901234567", "race@example.test");
        var booking = transactions.execute(status -> bookingService.create(customer.user(), request));
        transactions.executeWithoutResult(status -> attemptAdmin(admin, () ->
                operations.updateStatus(admin, fixture.trip().getId(), TripStatus.BOARDING)));

        List<String> outcomes = race(
                () -> attempt(() -> paymentService.confirm(customer.user(),
                        Objects.requireNonNull(booking).bookingId())),
                () -> attemptAdmin(admin, () -> operations.updateStatus(admin, fixture.trip().getId(),
                        TripStatus.DEPARTED)));
        assertThat(outcomes).contains("SUCCESS");
        assertThat(outcomes).allMatch(value -> value.equals("SUCCESS")
                || value.equals("PICKUP_UNRESOLVED"));
        assertThat(jdbc.queryForObject("SELECT status FROM trips WHERE id=?", String.class,
                fixture.trip().getId())).isEqualTo("BOARDING");
    }

    private CurrentUser admin(Fixture fixture) {
        User user = new User(); user.setFullName("M12 race admin");
        user.setEmail(UUID.randomUUID() + "@example.test"); user.setPhone("0901234567");
        user.setPasswordHash("not-used"); user.setStatus(UserStatus.ACTIVE); users.saveAndFlush(user);
        userRoles.saveAndFlush(new UserRole(user,
                roles.findByCode(RoleCode.OPERATOR_ADMIN).orElseThrow()));
        OperatorStaff membership = new OperatorStaff(); membership.setOperator(fixture.operator());
        membership.setUser(user); membership.setStaffCode(UUID.randomUUID().toString());
        membership.setStatus(ActiveStatus.ACTIVE); staff.saveAndFlush(membership);
        userIds.add(user.getId());
        M16BFixtures.crew(jdbc,fixture.operator().getId(),fixture.trip().getId(),user.getId());
        return new CurrentUser(user.getId(), List.of(RoleCode.OPERATOR_ADMIN));
    }

    private List<String> race(Callable<String> first, Callable<String> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return first.call(); });
            Future<String> b = executor.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return second.call(); });
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private String attempt(Runnable action) {
        try {
            action.run();
            return "SUCCESS";
        } catch (BusinessException ex) {
            return ex.getCode();
        }
    }

    private String attemptAdmin(CurrentUser admin, Runnable action) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(admin, null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR_ADMIN"))));
        SecurityContextHolder.setContext(context);
        try {
            return attempt(action);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
