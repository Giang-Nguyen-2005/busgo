package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.busgo.auth.AuthDtos.*;
import com.busgo.auth.AuthService;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.*;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.hold.SeatHoldCleanupJob;
import com.busgo.location.entity.Location;
import com.busgo.location.repository.LocationRepository;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.TransportOperatorRepository;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.user.entity.RoleCode;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M7SeatHoldIT extends JwtTestSupport {
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2030, 9, 20, 1, 0);

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
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
    @Autowired SeatHoldCleanupJob cleanup;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mvc.perform(post("/api/v1/seat-holds").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/seat-holds/token")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/seat-holds/token")).andExpect(status().isUnauthorized());
    }

    @Test
    void multiSeatHoldCanBeReadAndReleasedOnlyByItsOwner() throws Exception {
        Fixture f = fixture();
        UserAuth owner = customer("owner");
        UserAuth other = customer("other");
        String body = request(f, 0, 2, f.seats().get(0), f.seats().get(1));

        MvcResult created = mvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("data.tripId").value(f.trip().getId()))
                .andExpect(jsonPath("data.tripSeatIds.length()").value(2))
                .andExpect(jsonPath("data.pricePerSeat").value(200.00))
                .andExpect(jsonPath("data.totalPrice").value(400.00))
                .andExpect(jsonPath("data.status").value("ACTIVE"))
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.holdToken");

        Map<String, Object> state = jdbc.queryForMap("""
                SELECT COUNT(*) row_count, COUNT(DISTINCT hold_token) token_count,
                       COUNT(DISTINCT held_by_user_id) owner_count,
                       COUNT(DISTINCT hold_expires_at) expiry_count,
                       MIN(TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(6), hold_expires_at)) remaining
                FROM trip_seat_segment_inventory
                WHERE hold_token = ?
                """, token);
        assertThat(((Number) state.get("row_count")).intValue()).isEqualTo(4);
        assertThat(((Number) state.get("token_count")).intValue()).isOne();
        assertThat(((Number) state.get("owner_count")).intValue()).isOne();
        assertThat(((Number) state.get("expiry_count")).intValue()).isOne();
        assertThat(((Number) state.get("remaining")).longValue()).isBetween(590L, 600L);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_seat_segment_inventory
                WHERE hold_token=? AND held_by_user_id=?
                """, Integer.class, token, owner.id())).isEqualTo(4);

        seatMap(f, 0, 2).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(0));

        String expectedExpiry = com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.expiresAt");
        mvc.perform(get("/api/v1/seat-holds/{token}", token)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.pickup.locationId").value(f.locations().get(0).getId()))
                .andExpect(jsonPath("data.dropoff.locationId").value(f.locations().get(2).getId()))
                .andExpect(jsonPath("data.expiresAt").value(expectedExpiry));
        mvc.perform(get("/api/v1/seat-holds/{token}", token)
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("code").value("SEAT_HOLD_NOT_FOUND"));
        mvc.perform(delete("/api/v1/seat-holds/{token}", token)
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNoContent());
        assertThat(heldRows(token)).isEqualTo(4);

        mvc.perform(delete("/api/v1/seat-holds/{token}", token)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/seat-holds/{token}", token)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isNoContent());
        assertThat(heldRows(token)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_seat_segment_inventory
                WHERE trip_seat_id IN (?, ?) AND trip_segment_id IN (?, ?)
                  AND status = 'AVAILABLE' AND hold_token IS NULL
                  AND held_by_user_id IS NULL AND hold_expires_at IS NULL
                """, Integer.class, f.seats().get(0).getId(), f.seats().get(1).getId(),
                f.segments().get(0).getId(), f.segments().get(1).getId())).isEqualTo(4);
        seatMap(f, 0, 2).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(2));
    }

    @Test
    void validationAndUnavailableRowsPreserveMultiSeatAtomicity() throws Exception {
        Fixture f = fixture();
        UserAuth owner = customer("validation");
        TripSeat first = f.seats().get(0);
        TripSeat second = f.seats().get(1);

        mvc.perform(post("/api/v1/seat-holds").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(f, 0, 1, first, first)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));

        TripSeatSegmentInventory blocked = row(f, second, f.segments().get(0));
        blocked.setStatus(InventoryStatus.BOOKED);
        inventory.saveAndFlush(blocked);
        mvc.perform(post("/api/v1/seat-holds").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(f, 0, 1, first, second)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SEAT_NOT_AVAILABLE"));
        em.clear();
        assertThat(rowByIds(first.getId(), f.segments().get(0).getId()).getStatus())
                .isEqualTo(InventoryStatus.AVAILABLE);

        inventory.delete(row(f, first, f.segments().get(1)));
        inventory.flush();
        mvc.perform(post("/api/v1/seat-holds").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(f, 0, 2, first)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SEAT_NOT_AVAILABLE"));
    }

    @Test
    void expiredRowsAreReclaimedButActiveHeldRowsCannotBeStolen() throws Exception {
        Fixture f = fixture();
        UserAuth owner = customer("expiry");
        TripSeat seat = f.seats().get(0);
        TripSeatSegmentInventory first = row(f, seat, f.segments().get(0));
        first.setStatus(InventoryStatus.HELD); first.setHoldToken("expired");
        first.setHeldByUser(authUser(owner.id())); first.setHoldExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        inventory.saveAndFlush(first);

        mvc.perform(get("/api/v1/seat-holds/expired")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("data.status").value("EXPIRED"));

        MvcResult result = mvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request(f, 0, 1, seat)))
                .andExpect(status().isCreated()).andReturn();
        String replacement = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.holdToken");
        assertThat(replacement).isNotEqualTo("expired");

        mvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content(request(f, 0, 1, seat)))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SEAT_NOT_AVAILABLE"));
    }

    @Test
    void foreignSeatInvalidJourneyMissingFareAndBlockedInventoryFailSafely() throws Exception {
        Fixture f = fixture();
        Fixture otherTrip = fixture();
        UserAuth owner = customer("safe-errors");

        mvc.perform(post("/api/v1/seat-holds").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(f, 0, 1, otherTrip.seats().get(0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SEAT_NOT_AVAILABLE"));
        mvc.perform(post("/api/v1/seat-holds").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(f, 2, 1, f.seats().get(0))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_ROUTE_DIRECTION"));

        Long fromMaster = f.stops().get(0).getSourceRouteStop().getId();
        Long toMaster = f.stops().get(1).getSourceRouteStop().getId();
        jdbc.update("""
                UPDATE operator_route_fares SET status='INACTIVE'
                WHERE operator_route_id=? AND from_route_stop_id=? AND to_route_stop_id=?
                """, f.trip().getOperatorRoute().getId(), fromMaster, toMaster);
        em.clear();
        mvc.perform(post("/api/v1/seat-holds").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(f, 0, 1, f.seats().get(0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));

        jdbc.update("""
                UPDATE operator_route_fares SET status='ACTIVE'
                WHERE operator_route_id=? AND from_route_stop_id=? AND to_route_stop_id=?
                """, f.trip().getOperatorRoute().getId(), fromMaster, toMaster);
        jdbc.update("""
                UPDATE trip_seat_segment_inventory SET status='BLOCKED'
                WHERE trip_seat_id=? AND trip_segment_id=?
                """, f.seats().get(0).getId(), f.segments().get(0).getId());
        mvc.perform(post("/api/v1/seat-holds").header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(f, 0, 1, f.seats().get(0))))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("SEAT_NOT_AVAILABLE"));
    }

    @Test
    void cleanupUsesExpiryPredicateAndNeverTouchesBookedBlockedOrActiveHeldRows() {
        Fixture f = fixture();
        UserAuth owner = customer("cleanup");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        configure(row(f, f.seats().get(0), f.segments().get(0)), InventoryStatus.HELD,
                "old", owner.id(), now.minusMinutes(1));
        configure(row(f, f.seats().get(0), f.segments().get(1)), InventoryStatus.HELD,
                "active", owner.id(), now.plusMinutes(5));
        configure(row(f, f.seats().get(1), f.segments().get(0)), InventoryStatus.BOOKED,
                "booked", owner.id(), now.minusMinutes(1));
        configure(row(f, f.seats().get(1), f.segments().get(1)), InventoryStatus.BLOCKED,
                "blocked", owner.id(), now.minusMinutes(1));

        Map<String, Object> activeBefore = rawInventoryState(
                f.seats().get(0).getId(), f.segments().get(1).getId());
        Map<String, Object> bookedBefore = rawInventoryState(
                f.seats().get(1).getId(), f.segments().get(0).getId());
        Map<String, Object> blockedBefore = rawInventoryState(
                f.seats().get(1).getId(), f.segments().get(1).getId());
        LocalDateTime cleanupCutoff = now;
        int fixtureQualifyingRows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_seat_segment_inventory
                WHERE trip_seat_id IN (?, ?) AND trip_segment_id IN (?, ?)
                  AND status = 'HELD' AND hold_expires_at <= ?
                """, Integer.class, f.seats().get(0).getId(), f.seats().get(1).getId(),
                f.segments().get(0).getId(), f.segments().get(1).getId(), cleanupCutoff);
        int allQualifyingRows = jdbc.queryForObject("""
                SELECT COUNT(*) FROM trip_seat_segment_inventory
                WHERE status = 'HELD' AND hold_expires_at <= ?
                """, Integer.class, cleanupCutoff);
        assertThat(fixtureQualifyingRows).isEqualTo(1);
        assertThat(cleanup.releaseExpiredAt(cleanupCutoff)).isEqualTo(allQualifyingRows);
        em.clear();
        TripSeatSegmentInventory expired = rowByIds(
                f.seats().get(0).getId(), f.segments().get(0).getId());
        assertThat(expired.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
        assertThat(expired.getHoldToken()).isNull();
        assertThat(expired.getHeldByUser()).isNull();
        assertThat(expired.getHoldExpiresAt()).isNull();

        Map<String, Object> activeAfter = rawInventoryState(
                f.seats().get(0).getId(), f.segments().get(1).getId());
        Map<String, Object> bookedAfter = rawInventoryState(
                f.seats().get(1).getId(), f.segments().get(0).getId());
        Map<String, Object> blockedAfter = rawInventoryState(
                f.seats().get(1).getId(), f.segments().get(1).getId());
        assertThat(activeAfter).isEqualTo(activeBefore);
        assertThat(activeAfter).containsEntry("status", "HELD")
                .containsEntry("hold_token", "active");
        assertThat(bookedAfter).isEqualTo(bookedBefore);
        assertThat(bookedAfter).containsEntry("status", "BOOKED")
                .containsEntry("hold_token", "booked");
        assertThat(blockedAfter).isEqualTo(blockedBefore);
        assertThat(blockedAfter).containsEntry("status", "BLOCKED")
                .containsEntry("hold_token", "blocked");
    }

    private UserAuth customer(String name) {
        Registration registration = auth.register(new RegisterRequest(name,
                name + UUID.randomUUID() + "@example.test", "09" + Math.abs(new Random().nextLong() % 1000000000L),
                "test password"));
        CurrentUser current = new CurrentUser(registration.id(), List.of(RoleCode.CUSTOMER));
        return new UserAuth(registration.id(), jwt.issue(current, "access"));
    }

    private com.busgo.user.entity.User authUser(Long id) {
        return em.getReference(com.busgo.user.entity.User.class, id);
    }

    private int heldRows(String token) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM trip_seat_segment_inventory WHERE hold_token=?",
                Integer.class, token);
    }

    private Map<String, Object> rawInventoryState(Long seatId, Long segmentId) {
        return jdbc.queryForMap("""
                SELECT status, hold_token, held_by_user_id,
                       CAST(hold_expires_at AS CHAR) AS hold_expires_at
                FROM trip_seat_segment_inventory
                WHERE trip_seat_id=? AND trip_segment_id=?
                """, seatId, segmentId);
    }

    private String request(Fixture f, int pickup, int dropoff, TripSeat... selected) {
        String ids = Arrays.stream(selected).map(seat -> seat.getId().toString())
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {"tripId":%d,"pickupLocationId":%d,"dropoffLocationId":%d,"tripSeatIds":[%s]}
                """.formatted(f.trip().getId(), f.locations().get(pickup).getId(),
                        f.locations().get(dropoff).getId(), ids);
    }

    private ResultActions seatMap(Fixture f, int pickup, int dropoff) throws Exception {
        return mvc.perform(get("/api/v1/trips/{tripId}/seats", f.trip().getId())
                .param("pickupLocationId", f.locations().get(pickup).getId().toString())
                .param("dropoffLocationId", f.locations().get(dropoff).getId().toString()));
    }

    private void configure(TripSeatSegmentInventory row, InventoryStatus status, String token,
            Long userId, LocalDateTime expiry) {
        jdbc.update("""
                UPDATE trip_seat_segment_inventory
                SET status=?, hold_token=?, held_by_user_id=?, hold_expires_at=?,
                    updated_at=?, version=version+1
                WHERE id=?
                """, status.name(), token, userId, expiry, LocalDateTime.now(ZoneOffset.UTC),
                row.getId());
        em.clear();
    }

    private TripSeatSegmentInventory row(Fixture f, TripSeat seat, TripSegment segment) {
        return f.inventory().stream().filter(item -> item.getTripSeat().getId().equals(seat.getId())
                && item.getTripSegment().getId().equals(segment.getId())).findFirst().orElseThrow();
    }

    private TripSeatSegmentInventory rowByIds(Long seatId, Long segmentId) {
        return inventory.findAll().stream().filter(item -> item.getTripSeat().getId().equals(seatId)
                && item.getTripSegment().getId().equals(segmentId)).findFirst().orElseThrow();
    }

    private Fixture fixture() {
        List<Location> points = List.of(location("A"), location("B"), location("C"), location("D"));
        Route route = new Route(); route.setName("M7 " + UUID.randomUUID());
        route.setOriginLocation(points.get(0)); route.setDestinationLocation(points.get(3));
        route.setEstimatedDistanceKm(new BigDecimal("300")); route.setEstimatedDurationMinutes(180);
        route.setStatus(RouteStatus.ACTIVE); routes.saveAndFlush(route);
        List<RouteStop> masters = new ArrayList<>();
        for (int i = 0; i < 4; i++) masters.add(master(route, points.get(i), i + 1, i * 60));
        TransportOperator operator = new TransportOperator(); operator.setName("M7 " + UUID.randomUUID());
        operator.setCode(UUID.randomUUID().toString()); operator.setStatus(OperatorStatus.ACTIVE);
        operators.saveAndFlush(operator);
        OperatorRoute association = new OperatorRoute(); association.setOperator(operator);
        association.setRoute(route); association.setStatus(ActiveStatus.ACTIVE); operatorRoutes.saveAndFlush(association);
        BusType type = new BusType(); type.setName("M7 " + UUID.randomUUID()); type.setSeatCount(2);
        type.setStatus(ActiveStatus.ACTIVE); busTypes.saveAndFlush(type);
        List<SeatTemplate> templates = List.of(template(type, "A01", 1), template(type, "A02", 2));
        Bus bus = new Bus(); bus.setOperator(operator); bus.setBusType(type);
        bus.setLicensePlate("M7-" + UUID.randomUUID().toString().substring(0, 16));
        bus.setStatus(BusStatus.AVAILABLE); buses.saveAndFlush(bus);
        Trip trip = new Trip(); trip.setOperatorRoute(association); trip.setBus(bus);
        trip.setDepartureTime(DEPARTURE); trip.setEstimatedArrivalTime(DEPARTURE.plusHours(3));
        trip.setStatus(TripStatus.SCHEDULED); trips.saveAndFlush(trip);
        List<TripStop> snapshots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TripStop stop = new TripStop(); stop.setTrip(trip); stop.setSourceRouteStop(masters.get(i));
            stop.setLocation(points.get(i)); stop.setStopOrder(i + 1); stop.setAllowPickup(i < 3);
            stop.setAllowDropoff(i > 0); stop.setPlannedArrivalTime(i == 0 ? null : DEPARTURE.plusHours(i));
            stop.setPlannedDepartureTime(i == 3 ? null : DEPARTURE.plusHours(i));
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
        rows = inventory.saveAllAndFlush(rows);
        for (int from = 0; from < 3; from++) for (int to = from + 1; to < 4; to++) {
            OperatorRouteFare fare = new OperatorRouteFare(); fare.setOperatorRoute(association);
            fare.setFromRouteStop(masters.get(from)); fare.setToRouteStop(masters.get(to));
            fare.setPrice(BigDecimal.valueOf((to - from) * 100L)); fare.setStatus(ActiveStatus.ACTIVE);
            fares.saveAndFlush(fare);
        }
        return new Fixture(points, trip, snapshots, generatedSegments, generatedSeats, rows);
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

    private record UserAuth(Long id, String token) {}
    private record Fixture(List<Location> locations, Trip trip, List<TripStop> stops,
            List<TripSegment> segments, List<TripSeat> seats,
            List<TripSeatSegmentInventory> inventory) {}
}
