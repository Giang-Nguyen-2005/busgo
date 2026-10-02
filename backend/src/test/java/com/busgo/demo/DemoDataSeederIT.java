package com.busgo.demo;

import static org.assertj.core.api.Assertions.*;

import com.busgo.JwtTestSupport;
import com.busgo.auth.AuthDtos.RegisterRequest;
import com.busgo.auth.AuthService;
import com.busgo.booking.BookingDtos.CreateBookingRequest;
import com.busgo.booking.BookingService;
import com.busgo.common.security.CurrentUser;
import com.busgo.hold.SeatHoldDtos.CreateSeatHoldRequest;
import com.busgo.hold.SeatHoldService;
import com.busgo.payment.PaymentTicketService;
import com.busgo.trip.TripAggregateCreator;
import com.busgo.trip.repository.TripRepository;
import com.busgo.fleet.repository.BusRepository;
import com.busgo.user.entity.RoleCode;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Real MySQL/production aggregate tests; every ordinary test rolls its fixtures back. */
@SpringBootTest(properties="busgo.demo.reset-unbooked-trips=true")
@ActiveProfiles("dev")
@Import(DemoDataSeederIT.TimeConfiguration.class)
@Transactional
class DemoDataSeederIT extends JwtTestSupport {
    @Autowired ConfigurableApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired AuthService auth;
    @Autowired SeatHoldService holds;
    @Autowired BookingService bookings;
    @Autowired PaymentTicketService payments;
    @Autowired PasswordEncoder passwords;
    @Autowired TripAggregateCreator creator;
    @Autowired TripRepository trips;
    @Autowired BusRepository buses;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired EntityManager entityManager;
    DemoDataSeeder seeder;

    @BeforeEach void prepare() {
        clock.now.set(Instant.parse("2036-01-01T00:00:00Z"));
        // Post-process the real component (including transactional advice), without activating
        // demo/ApplicationRunner at context startup. No mock repositories or aggregate creator.
        seeder = context.getAutowireCapableBeanFactory().createBean(DemoDataSeeder.class);
    }

    @Test void firstSeedAndTwoSameDateRerunsAreCreateOnly() {
        var first = seeder.seed();
        assertThat(first.createdTrips()).isEqualTo(21);
        assertThat(first.skippedTrips()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForList("SELECT r.code FROM user_roles ur JOIN roles r ON r.id=ur.role_id JOIN users u ON u.id=ur.user_id WHERE u.email=?",
                String.class, DemoDataSeeder.OPERATOR_STAFF_EMAIL)).containsExactly("OPERATOR_STAFF");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory i JOIN trip_seats s ON s.id=i.trip_seat_id WHERE s.trip_id=? AND i.status='AVAILABLE'", Long.class, mainTrip())).isGreaterThanOrEqualTo(2);
        Map<String, List<Map<String, Object>>> before = snapshot();
        for (int i = 0; i < 2; i++) {
            var repeated = seeder.seed();
            assertThat(repeated.createdTrips()).isZero();
            assertThat(repeated.reusedTrips()).isEqualTo(21);
            assertThat(snapshot()).isEqualTo(before);
        }
        seeder.run(null); // Legacy reset flag must be harmless, even with existing fixture trips.
        assertThat(snapshot()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory", Long.class)).isEqualTo(882);
    }

    @Test void consecutiveDatesNeverConflictWithTheFixtureSchedule() {
        seeder.seed();
        for (int day = 1; day <= 5; day++) {
            clock.now.set(Instant.parse("2036-01-01T00:00:00Z").plus(Duration.ofDays(day)));
            var next = seeder.seed();
            assertThat(next.createdTrips()).isEqualTo(7);
            assertThat(next.reusedTrips()).isEqualTo(14);
            assertThat(next.skippedTrips()).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trips a JOIN trips b ON a.bus_id=b.bus_id AND a.id<b.id AND a.departure_time<b.estimated_arrival_time AND a.estimated_arrival_time>b.departure_time", Long.class)).isZero();
    }

    @Test void manualOverlappingTripIsSkippedAndNonOverlappingTripSurvives() {
        seeder.seed();
        clock.now.set(Instant.parse("2036-01-02T00:00:00Z"));
        var original = trips.findById(mainTrip()).orElseThrow();
        long operator = original.getOperatorRoute().getOperator().getId();
        long association = original.getOperatorRoute().getId();
        long bus = original.getBus().getId();
        // Jan 5 is the newly rolling date. Both manual trips use the production creator.
        long overlapping = creator.create(operator, association, bus, LocalDateTime.parse("2036-01-04T23:00:00")).trip().getId();
        long separate = creator.create(operator, association, bus, LocalDateTime.parse("2036-01-05T07:00:00")).trip().getId();
        var before = snapshot();
        var next = seeder.seed();
        assertThat(next.skippedTrips()).isEqualTo(1);
        assertThat(next.createdTrips()).isEqualTo(6);
        assertThat(row("trips", overlapping)).isEqualTo(before.get("trips").stream().filter(r -> r.get("id").equals(overlapping)).findFirst().orElseThrow());
        assertThat(row("trips", separate)).isEqualTo(before.get("trips").stream().filter(r -> r.get("id").equals(separate)).findFirst().orElseThrow());
        assertOldRowsUnchanged(before);
    }

    @Test void pendingConfirmedBookingsTicketsAndActiveHoldArePreserved() {
        seeder.seed();
        long trip = mainTrip();
        var registration = auth.register(new RegisterRequest("M15 Customer", UUID.randomUUID()+"@example.test", "0900000015", "test password"));
        CurrentUser owner = new CurrentUser(registration.id(), List.of(RoleCode.CUSTOMER));
        List<Long> available = jdbc.queryForList("SELECT s.id FROM trip_seats s JOIN trip_seat_segment_inventory i ON i.trip_seat_id=s.id WHERE s.trip_id=? AND i.status='AVAILABLE' ORDER BY s.id LIMIT 3", Long.class, trip);
        long pickup = jdbc.queryForObject("SELECT location_id FROM trip_stops WHERE trip_id=? AND stop_order=1", Long.class, trip);
        long dropoff = jdbc.queryForObject("SELECT location_id FROM trip_stops WHERE trip_id=? AND stop_order=2", Long.class, trip);
        var first = holds.create(owner, new CreateSeatHoldRequest(trip, pickup, dropoff, List.of(available.get(0))));
        var pending = bookings.create(owner, new CreateBookingRequest(first.holdToken(), "Customer", "0900000015", "customer@example.test"));
        var second = holds.create(owner, new CreateSeatHoldRequest(trip, pickup, dropoff, List.of(available.get(1))));
        var paid = bookings.create(owner, new CreateBookingRequest(second.holdToken(), "Customer", "0900000015", "customer@example.test"));
        payments.confirm(owner, paid.bookingId());
        var held = holds.create(owner, new CreateSeatHoldRequest(trip, pickup, dropoff, List.of(available.get(2))));
        var before = snapshot();
        seeder.seed();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(bookings.detail(owner, pending.bookingId()).status().name()).isEqualTo("PENDING");
        assertThat(payments.ticket(owner, paid.bookingId()).tickets()).hasSize(1);
        assertThat(holds.get(owner, held.holdToken()).status().name()).isEqualTo("ACTIVE");
    }

    static Stream<String> mutableChanges() {
        return Stream.of(
                "UPDATE trips SET status='BOARDING' WHERE id=(SELECT id FROM (SELECT MIN(id) id FROM trips) x)",
                "UPDATE transport_operators SET status='INACTIVE' WHERE code='DEMO-ANPHU'",
                "UPDATE buses SET status='MAINTENANCE' WHERE license_plate='51B-770.01'",
                "UPDATE operator_route_fares SET price=123456, status='INACTIVE' WHERE id=(SELECT id FROM (SELECT MIN(id) id FROM operator_route_fares) x)",
                "UPDATE users SET status='LOCKED' WHERE email='operator.admin@anphu-demo.example'",
                "UPDATE operator_staff SET status='INACTIVE' WHERE staff_code='DEMO-ANPHU-ADMIN'",
                "UPDATE operator_staff SET status='INACTIVE' WHERE staff_code='DEMO-ANPHU-STAFF'",
                "UPDATE trip_seat_segment_inventory SET status='AVAILABLE', version=version+1 WHERE status='BLOCKED'");
    }

    @ParameterizedTest @MethodSource("mutableChanges")
    void manuallyChangedStateAndInventoryAreNeverRewritten(String sql) {
        seeder.seed();
        jdbc.update(sql);
        entityManager.clear(); // Simulate a fresh startup, rather than reading managed stale entities.
        var before = snapshot();
        seeder.seed();
        assertThat(snapshot()).isEqualTo(before);
        clock.now.set(clock.instant().plus(Duration.ofDays(1)));
        seeder.seed();
        assertOldRowsUnchanged(before);
    }

    @Test void passwordsAreNeverResetForEitherAccount() {
        seeder.seed();
        String changed = passwords.encode("Changed demo password");
        jdbc.update("UPDATE users SET password_hash=? WHERE email IN (?,?,?)", changed,
                DemoDataSeeder.OPERATOR_ADMIN_EMAIL, DemoDataSeeder.OPERATOR_STAFF_EMAIL,
                DemoDataSeeder.SECONDARY_ADMIN_EMAIL);
        entityManager.clear();
        var before = snapshot();
        seeder.seed();
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"CUSTOMER", "SYSTEM_ADMIN", "OPERATOR_STAFF"})
    void extraAdminRolesAreRejectedWithoutRepair(String role) {
        seeder.seed();
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE u.email=? AND r.code=?", DemoDataSeeder.OPERATOR_ADMIN_EMAIL, role);
        assertThatThrownBy(seeder::seed).isInstanceOf(IllegalStateException.class).hasMessageContaining("account");
    }

    @ParameterizedTest @ValueSource(strings={"CUSTOMER", "SYSTEM_ADMIN", "OPERATOR_ADMIN"})
    void extraStaffRolesAreRejectedWithoutRepair(String role) {
        seeder.seed();
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r WHERE u.email=? AND r.code=?", DemoDataSeeder.OPERATOR_STAFF_EMAIL, role);
        assertThatThrownBy(seeder::seed).isInstanceOf(IllegalStateException.class).hasMessageContaining("account");
    }

    @Test void wrongRoleAndReservedEmailIdentityAreRejected() {
        seeder.seed();
        jdbc.update("UPDATE users SET full_name='Unrelated user' WHERE email=?", DemoDataSeeder.OPERATOR_ADMIN_EMAIL);
        entityManager.clear();
        assertThatThrownBy(seeder::seed).hasMessageContaining("Demo fixture collision");
    }

    @Test void unrelatedCatalogueCollisionIsRejected() {
        seeder.seed();
        jdbc.update("UPDATE locations SET address='Unrelated address' WHERE name='Bến xe Đà Lạt'");
        entityManager.clear();
        assertThatThrownBy(seeder::seed).hasMessageContaining("location DALAT");
    }

    @Test void soleWrongRoleIsRejectedInsteadOfGrantedOperatorPrivileges() {
        seeder.seed();
        long user = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, DemoDataSeeder.OPERATOR_ADMIN_EMAIL);
        jdbc.update("DELETE FROM user_roles WHERE user_id=?", user);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE code='CUSTOMER'", user);
        entityManager.clear();
        assertThatThrownBy(seeder::seed).hasMessageContaining("account");
        assertThat(jdbc.queryForList("SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id WHERE ur.user_id=?", String.class, user)).containsExactly("CUSTOMER");
    }

    @Test void foreignMembershipIsRejectedEvenIfInactive() {
        seeder.seed();
        jdbc.update("INSERT INTO operator_staff(operator_id,user_id,staff_code,status,created_at) SELECT o.id,u.id,'UNRELATED','INACTIVE',UTC_TIMESTAMP(6) FROM transport_operators o CROSS JOIN users u WHERE o.code='DEMO-TAYNGUYEN' AND u.email=?", DemoDataSeeder.OPERATOR_ADMIN_EMAIL);
        entityManager.clear();
        assertThatThrownBy(seeder::seed).hasMessageContaining("account");
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void lateCollisionRollsBackAllNewFixtureWrites() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        // Reserve the staff email with a non-fixture user, after operator/admin creation would occur.
        long id = tx.execute(status -> auth.register(new RegisterRequest("Unrelated", DemoDataSeeder.OPERATOR_STAFF_EMAIL, "0900000099", "test password")).id());
        try {
            var before = snapshot();
            assertThatThrownBy(seeder::seed).hasMessageContaining("account");
            assertThat(snapshot()).isEqualTo(before);
        } finally {
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM user_roles WHERE user_id=?", id);
                jdbc.update("DELETE FROM users WHERE id=?", id);
            });
        }
    }

    private long mainTrip() {
        return jdbc.queryForObject("SELECT MIN(t.id) FROM trips t JOIN buses b ON b.id=t.bus_id JOIN operator_routes o ON o.id=t.operator_route_id JOIN routes r ON r.id=o.route_id WHERE b.license_plate='51B-770.01' AND r.name='TP. Hồ Chí Minh → Đà Lạt'", Long.class);
    }
    private static final List<String> TABLES = List.of("transport_operators", "locations", "routes", "route_stops", "operator_routes", "operator_route_fares", "bus_types", "seat_templates", "buses", "trips", "trip_stops", "trip_segments", "trip_seats", "trip_seat_segment_inventory", "users", "user_roles", "operator_staff", "bookings", "booking_items", "payments", "tickets", "booking_status_history", "refresh_tokens");
    private Map<String,List<Map<String,Object>>> snapshot() {
        Map<String,List<Map<String,Object>>> result = new LinkedHashMap<>();
        TABLES.forEach(table -> result.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + (table.equals("user_roles") ? "user_id,role_id" : "id"))));
        return result;
    }
    private Map<String,Object> row(String table, long id) { return jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?", id); }
    private void assertOldRowsUnchanged(Map<String,List<Map<String,Object>>> before) {
        var after = snapshot();
        before.forEach((table, rows) -> assertThat(after.get(table)).containsAll(rows));
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock demoTestClock() { return new MutableClock(); }
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2036-01-01T00:00:00Z"));
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return new Clock() {
            @Override public ZoneId getZone() { return zone; }
            @Override public Clock withZone(ZoneId other) { return MutableClock.this.withZone(other); }
            @Override public Instant instant() { return now.get(); }
        }; }
        @Override public Instant instant() { return now.get(); }
    }
}
