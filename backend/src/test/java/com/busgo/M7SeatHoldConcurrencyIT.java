package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;

import com.busgo.auth.AuthDtos.*;
import com.busgo.auth.AuthService;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.hold.SeatHoldDtos.CreateSeatHoldRequest;
import com.busgo.hold.SeatHoldService;
import com.busgo.location.entity.Location;
import com.busgo.location.repository.LocationRepository;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.TransportOperatorRepository;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.user.entity.RoleCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** Uses separate committed MySQL transactions so SELECT ... FOR UPDATE is exercised for real. */
@SpringBootTest
@ActiveProfiles("dev")
class M7SeatHoldConcurrencyIT extends JwtTestSupport {
    @Autowired SeatHoldService holds;
    @Autowired AuthService auth;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired LocationRepository locations;
    @Autowired RouteRepository routes;
    @Autowired RouteStopRepository routeStops;
    @Autowired TransportOperatorRepository operators;
    @Autowired OperatorRouteRepository operatorRoutes;
    @Autowired OperatorRouteFareRepository fares;
    @Autowired BusTypeRepository busTypes;
    @Autowired SeatTemplateRepository seatTemplates;
    @Autowired BusRepository buses;
    @Autowired TripRepository trips;
    @Autowired TripStopSnapshotRepository tripStops;
    @Autowired TripSegmentRepository segments;
    @Autowired TripSeatRepository tripSeats;
    @Autowired TripSeatSegmentInventoryRepository inventory;

    private final List<Fixture> fixtures = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    @AfterEach
    void cleanOwnedFixtures() {
        for (Fixture f : fixtures) {
            jdbc.update("DELETE FROM trip_seat_segment_inventory WHERE trip_seat_id IN (SELECT id FROM trip_seats WHERE trip_id=?)", f.tripId());
            jdbc.update("DELETE FROM trip_seats WHERE trip_id=?", f.tripId());
            jdbc.update("DELETE FROM trip_segments WHERE trip_id=?", f.tripId());
            jdbc.update("DELETE FROM trip_stops WHERE trip_id=?", f.tripId());
            jdbc.update("DELETE FROM trips WHERE id=?", f.tripId());
            jdbc.update("DELETE FROM operator_route_fares WHERE operator_route_id=?", f.operatorRouteId());
            jdbc.update("DELETE FROM buses WHERE id=?", f.busId());
            jdbc.update("DELETE FROM seat_templates WHERE bus_type_id=?", f.busTypeId());
            jdbc.update("DELETE FROM bus_types WHERE id=?", f.busTypeId());
            jdbc.update("DELETE FROM operator_routes WHERE id=?", f.operatorRouteId());
            jdbc.update("DELETE FROM route_stops WHERE route_id=?", f.routeId());
            jdbc.update("DELETE FROM routes WHERE id=?", f.routeId());
            jdbc.update("DELETE FROM transport_operators WHERE id=?", f.operatorId());
            for (Long locationId : f.locationIds()) jdbc.update("DELETE FROM locations WHERE id=?", locationId);
        }
        for (Long userId : userIds) {
            jdbc.update("DELETE FROM refresh_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_roles WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users WHERE id=?", userId);
        }
    }

    @Test
    void concurrentOverlappingHoldHasExactlyOneWinnerAndCleanState() throws Exception {
        Fixture f = committedFixture();
        CurrentUser firstUser = customer("race-a");
        CurrentUser secondUser = customer("race-b");
        CreateSeatHoldRequest request = new CreateSeatHoldRequest(f.tripId(),
                f.locationIds().get(0), f.locationIds().get(2), List.of(f.seatIds().get(0)));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(attempt(barrier, firstUser, request));
            Future<Boolean> second = executor.submit(attempt(barrier, secondUser, request));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            Map<String, Object> state = jdbc.queryForMap("""
                    SELECT COUNT(*) rows_held, COUNT(DISTINCT hold_token) tokens,
                           COUNT(DISTINCT held_by_user_id) owners
                    FROM trip_seat_segment_inventory
                    WHERE trip_seat_id=? AND trip_segment_id IN (?, ?) AND status='HELD'
                    """, f.seatIds().get(0), f.segmentIds().get(0), f.segmentIds().get(1));
            assertThat(((Number) state.get("rows_held")).intValue()).isEqualTo(2);
            assertThat(((Number) state.get("tokens")).intValue()).isOne();
            assertThat(((Number) state.get("owners")).intValue()).isOne();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentNonOverlappingJourneysCanReuseTheSamePhysicalSeat() throws Exception {
        Fixture f = committedFixture();
        CurrentUser firstUser = customer("reuse-a");
        CurrentUser secondUser = customer("reuse-b");
        CreateSeatHoldRequest firstRequest = new CreateSeatHoldRequest(f.tripId(),
                f.locationIds().get(0), f.locationIds().get(1), List.of(f.seatIds().get(0)));
        CreateSeatHoldRequest secondRequest = new CreateSeatHoldRequest(f.tripId(),
                f.locationIds().get(1), f.locationIds().get(3), List.of(f.seatIds().get(0)));
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(attempt(barrier, firstUser, firstRequest));
            Future<Boolean> second = executor.submit(attempt(barrier, secondUser, secondRequest));
            assertThat(first.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(DISTINCT hold_token) FROM trip_seat_segment_inventory
                    WHERE trip_seat_id=? AND status='HELD'
                    """, Integer.class, f.seatIds().get(0))).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Boolean> attempt(CyclicBarrier barrier, CurrentUser user,
            CreateSeatHoldRequest request) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                holds.create(user, request);
                return true;
            } catch (BusinessException ex) {
                assertThat(ex.getCode()).isEqualTo("SEAT_NOT_AVAILABLE");
                return false;
            }
        };
    }

    private CurrentUser customer(String prefix) {
        Registration registration = auth.register(new RegisterRequest(prefix,
                prefix + UUID.randomUUID() + "@example.test",
                "08" + UUID.randomUUID().toString().replace("-", "").substring(0, 9),
                "test password"));
        userIds.add(registration.id());
        return new CurrentUser(registration.id(), List.of(RoleCode.CUSTOMER));
    }

    private Fixture committedFixture() {
        Fixture fixture = transactions.execute(status -> createFixture());
        fixtures.add(Objects.requireNonNull(fixture));
        return fixture;
    }

    private Fixture createFixture() {
        List<Location> points = List.of(location("A"), location("B"), location("C"), location("D"));
        Route route = new Route(); route.setName("M7 concurrency " + UUID.randomUUID());
        route.setOriginLocation(points.get(0)); route.setDestinationLocation(points.get(3));
        route.setEstimatedDistanceKm(new BigDecimal("300")); route.setEstimatedDurationMinutes(180);
        route.setStatus(RouteStatus.ACTIVE); routes.saveAndFlush(route);
        List<RouteStop> masters = new ArrayList<>();
        for (int i = 0; i < 4; i++) masters.add(master(route, points.get(i), i + 1, i * 60));
        TransportOperator operator = new TransportOperator(); operator.setName("M7 concurrency " + UUID.randomUUID());
        operator.setCode(UUID.randomUUID().toString()); operator.setStatus(OperatorStatus.ACTIVE);
        operators.saveAndFlush(operator);
        OperatorRoute association = new OperatorRoute(); association.setOperator(operator);
        association.setRoute(route); association.setStatus(ActiveStatus.ACTIVE); operatorRoutes.saveAndFlush(association);
        BusType type = new BusType(); type.setName("M7 concurrency " + UUID.randomUUID());
        type.setSeatCount(2); type.setStatus(ActiveStatus.ACTIVE); busTypes.saveAndFlush(type);
        List<SeatTemplate> templates = List.of(template(type, "A01", 1), template(type, "A02", 2));
        Bus bus = new Bus(); bus.setOperator(operator); bus.setBusType(type);
        bus.setLicensePlate("M7C-" + UUID.randomUUID().toString().substring(0, 15));
        bus.setStatus(BusStatus.AVAILABLE); buses.saveAndFlush(bus);
        LocalDateTime departure = LocalDateTime.of(2030, 9, 20, 1, 0);
        Trip trip = new Trip(); trip.setOperatorRoute(association); trip.setBus(bus);
        trip.setDepartureTime(departure); trip.setEstimatedArrivalTime(departure.plusHours(3));
        trip.setStatus(TripStatus.SCHEDULED); trips.saveAndFlush(trip);
        List<TripStop> snapshots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TripStop stop = new TripStop(); stop.setTrip(trip); stop.setSourceRouteStop(masters.get(i));
            stop.setLocation(points.get(i)); stop.setStopOrder(i + 1); stop.setAllowPickup(i < 3);
            stop.setAllowDropoff(i > 0); stop.setPlannedArrivalTime(i == 0 ? null : departure.plusHours(i));
            stop.setPlannedDepartureTime(i == 3 ? null : departure.plusHours(i));
            stop.setStatus(ActiveStatus.ACTIVE); snapshots.add(stop);
        }
        snapshots = tripStops.saveAllAndFlush(snapshots);
        List<TripSegment> generatedSegments = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TripSegment segment = new TripSegment(); segment.setTrip(trip);
            segment.setFromTripStop(snapshots.get(i)); segment.setToTripStop(snapshots.get(i + 1));
            segment.setSegmentOrder(i + 1); generatedSegments.add(segment);
        }
        generatedSegments = segments.saveAllAndFlush(generatedSegments);
        List<TripSeat> generatedSeats = new ArrayList<>();
        for (SeatTemplate source : templates) {
            TripSeat seat = new TripSeat(); seat.setTrip(trip); seat.setSourceSeatTemplate(source);
            seat.setSeatCode(source.getSeatCode()); seat.setRow(1); seat.setColumn(source.getColumn());
            seat.setFloor(1); seat.setSeatType(SeatType.STANDARD); generatedSeats.add(seat);
        }
        generatedSeats = tripSeats.saveAllAndFlush(generatedSeats);
        List<TripSeatSegmentInventory> rows = new ArrayList<>();
        for (TripSeat seat : generatedSeats) for (TripSegment segment : generatedSegments) {
            TripSeatSegmentInventory row = new TripSeatSegmentInventory(); row.setTripSeat(seat);
            row.setTripSegment(segment); row.setStatus(InventoryStatus.AVAILABLE); rows.add(row);
        }
        inventory.saveAllAndFlush(rows);
        for (int from = 0; from < 3; from++) for (int to = from + 1; to < 4; to++) {
            OperatorRouteFare fare = new OperatorRouteFare(); fare.setOperatorRoute(association);
            fare.setFromRouteStop(masters.get(from)); fare.setToRouteStop(masters.get(to));
            fare.setPrice(BigDecimal.valueOf((to - from) * 100L)); fare.setStatus(ActiveStatus.ACTIVE);
            fares.saveAndFlush(fare);
        }
        return new Fixture(trip.getId(), points.stream().map(Location::getId).toList(),
                generatedSeats.stream().map(TripSeat::getId).toList(),
                generatedSegments.stream().map(TripSegment::getId).toList(), association.getId(),
                operator.getId(), route.getId(), bus.getId(), type.getId());
    }

    private Location location(String name) {
        Location value = new Location(); value.setName(name + UUID.randomUUID()); value.setProvince("Test");
        value.setDistrict("Test"); value.setStatus(ActiveStatus.ACTIVE); return locations.saveAndFlush(value);
    }

    private RouteStop master(Route route, Location location, int order, int offset) {
        RouteStop value = new RouteStop(); value.setRoute(route); value.setLocation(location);
        value.setStopOrder(order); value.setEstimatedOffsetMinutes(offset); value.setAllowPickup(order < 4);
        value.setAllowDropoff(order > 1); value.setStatus(ActiveStatus.ACTIVE); return routeStops.saveAndFlush(value);
    }

    private SeatTemplate template(BusType type, String code, int column) {
        SeatTemplate value = new SeatTemplate(); value.setBusType(type); value.setSeatCode(code);
        value.setRow(1); value.setColumn(column); value.setFloor(1); value.setSeatType(SeatType.STANDARD);
        value.setActive(true); return seatTemplates.saveAndFlush(value);
    }

    private record Fixture(Long tripId, List<Long> locationIds, List<Long> seatIds,
            List<Long> segmentIds, Long operatorRouteId, Long operatorId, Long routeId,
            Long busId, Long busTypeId) {}
}
