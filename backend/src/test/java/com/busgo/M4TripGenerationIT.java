package com.busgo;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.*;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.location.entity.Location;
import com.busgo.location.repository.LocationRepository;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.*;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import com.busgo.user.entity.*;
import com.busgo.user.repository.*;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M4TripGenerationIT extends JwtTestSupport {
    private static final OffsetDateTime DEPARTURE = OffsetDateTime.parse("2030-09-20T08:00:00+07:00");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserRoleRepository userRoles;
    @Autowired TransportOperatorRepository operators;
    @Autowired OperatorStaffRepository staff;
    @Autowired LocationRepository locations;
    @Autowired RouteRepository routes;
    @Autowired RouteStopRepository routeStops;
    @Autowired OperatorRouteRepository operatorRoutes;
    @Autowired BusTypeRepository busTypes;
    @Autowired SeatTemplateRepository seatTemplates;
    @Autowired BusRepository buses;
    @Autowired TripRepository trips;
    @Autowired TripStopSnapshotRepository tripStops;
    @Autowired TripSegmentRepository segments;
    @Autowired TripSeatRepository tripSeats;
    @Autowired TripSeatSegmentInventoryRepository inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void customerCannotCreateListOrReadOperatorTrips() throws Exception {
        String token = token(account(RoleCode.CUSTOMER, null));
        getTrips(token).andExpect(status().isForbidden());
        postTrip(token, 1, 1, DEPARTURE).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/operator/trips/1").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validCreationBuildsCompleteAggregateAndNormalizesTimeToUtc() throws Exception {
        Fixture f = fixture();
        JsonNode created = data(postTrip(f.admin().token(), f.operatorRoute().getId(), f.bus().getId(), DEPARTURE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("data.status").value("SCHEDULED"))
                .andExpect(jsonPath("data.departureTime").value("2030-09-20T01:00:00Z"))
                .andExpect(jsonPath("data.estimatedArrivalTime").value("2030-09-20T03:00:00Z"))
                .andExpect(jsonPath("data.seatCount").value(2))
                .andExpect(jsonPath("data.segmentCount").value(2)));
        long tripId = created.path("id").asLong();

        org.assertj.core.api.Assertions.assertThat(tripStops.countByTripId(tripId)).isEqualTo(3);
        org.assertj.core.api.Assertions.assertThat(segments.countByTripId(tripId)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(tripSeats.countByTripId(tripId)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(inventory.countByTripSeatTripId(tripId)).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(inventory.countByTripSeatTripIdAndStatus(tripId, InventoryStatus.AVAILABLE)).isEqualTo(4);
        Map<String, Object> holds = jdbc.queryForMap("""
                SELECT COUNT(*) total,
                       SUM(hold_token IS NULL) null_tokens,
                       SUM(held_by_user_id IS NULL) null_users,
                       SUM(hold_expires_at IS NULL) null_expiries
                FROM trip_seat_segment_inventory i
                JOIN trip_seats s ON s.id=i.trip_seat_id
                WHERE s.trip_id=?
                """, tripId);
        assertThat(((Number) holds.get("total")).longValue()).isEqualTo(4);
        assertThat(((Number) holds.get("null_tokens")).longValue()).isEqualTo(4);
        assertThat(((Number) holds.get("null_users")).longValue()).isEqualTo(4);
        assertThat(((Number) holds.get("null_expiries")).longValue()).isEqualTo(4);
    }

    @Test
    void tripStopAndSeatSnapshotsRemainStableAfterMasterChanges() throws Exception {
        Fixture f = fixture();
        f.routeStops().get(0).setEstimatedOffsetMinutes(15);
        routeStops.saveAndFlush(f.routeStops().get(0));
        long tripId = data(postTrip(f.admin().token(), f.operatorRoute().getId(), f.bus().getId(), DEPARTURE)
                .andExpect(status().isCreated())).path("id").asLong();

        RouteStop middle = f.routeStops().get(1);
        middle.setAllowPickup(false);
        middle.setEstimatedOffsetMinutes(75);
        routeStops.saveAndFlush(middle);
        SeatTemplate sourceSeat = f.seats().get(0);
        sourceSeat.setSeatCode("CHANGED");
        sourceSeat.setRow(9);
        seatTemplates.saveAndFlush(sourceSeat);
        em.clear();

        mvc.perform(get("/api/v1/operator/trips/{id}", tripId).header("Authorization", bearer(f.admin().token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data.stops.length()").value(3))
                .andExpect(jsonPath("data.stops[0].plannedArrivalTime").doesNotExist())
                .andExpect(jsonPath("data.stops[0].plannedDepartureTime").value("2030-09-20T01:00:00Z"))
                .andExpect(jsonPath("data.stops[1].plannedArrivalTime").value("2030-09-20T02:00:00Z"))
                .andExpect(jsonPath("data.stops[1].plannedDepartureTime").value("2030-09-20T02:00:00Z"))
                .andExpect(jsonPath("data.stops[1].allowPickup").value(true))
                .andExpect(jsonPath("data.stops[2].plannedArrivalTime").value("2030-09-20T03:00:00Z"))
                .andExpect(jsonPath("data.stops[2].plannedDepartureTime").doesNotExist())
                .andExpect(jsonPath("data.segments.length()").value(2))
                .andExpect(jsonPath("data.segments[0].segmentOrder").value(1))
                .andExpect(jsonPath("data.segments[1].segmentOrder").value(2))
                .andExpect(jsonPath("data.seats.length()").value(2))
                .andExpect(jsonPath("data.seats[0].seatCode").value("A01"))
                .andExpect(jsonPath("data.seats[0].row").value(1));
    }

    @Test
    void foreignOperatorRouteAndBusAreNotUsable() throws Exception {
        Fixture own = fixture();
        Fixture foreign = fixture();
        postTrip(own.admin().token(), foreign.operatorRoute().getId(), own.bus().getId(), DEPARTURE)
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("OPERATOR_ROUTE_NOT_FOUND"));
        postTrip(own.admin().token(), own.operatorRoute().getId(), foreign.bus().getId(), DEPARTURE)
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("BUS_NOT_FOUND"));
    }

    @Test
    void inactiveAssociationAndUnderlyingRouteAreRejected() throws Exception {
        Fixture associationInactive = fixture();
        associationInactive.operatorRoute().setStatus(ActiveStatus.INACTIVE);
        operatorRoutes.saveAndFlush(associationInactive.operatorRoute());
        postTrip(associationInactive.admin().token(), associationInactive.operatorRoute().getId(),
                associationInactive.bus().getId(), DEPARTURE)
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("OPERATOR_ROUTE_INACTIVE"));

        Fixture routeInactive = fixture();
        routeInactive.route().setStatus(RouteStatus.INACTIVE);
        routes.saveAndFlush(routeInactive.route());
        postTrip(routeInactive.admin().token(), routeInactive.operatorRoute().getId(), routeInactive.bus().getId(), DEPARTURE)
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("INVALID_ROUTE"));
    }

    @Test
    void unavailableBusInactiveBusTypeAndMissingSeatLayoutAreRejected() throws Exception {
        Fixture unavailable = fixture();
        unavailable.bus().setStatus(BusStatus.MAINTENANCE);
        buses.saveAndFlush(unavailable.bus());
        postTrip(unavailable.admin().token(), unavailable.operatorRoute().getId(), unavailable.bus().getId(), DEPARTURE)
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("BUS_NOT_AVAILABLE"));

        Fixture inactiveType = fixture();
        inactiveType.busType().setStatus(ActiveStatus.INACTIVE);
        busTypes.saveAndFlush(inactiveType.busType());
        postTrip(inactiveType.admin().token(), inactiveType.operatorRoute().getId(), inactiveType.bus().getId(), DEPARTURE)
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("BUS_TYPE_NOT_FOUND"));

        Fixture noLayout = fixture();
        noLayout.seats().forEach(seat -> seat.setActive(false));
        seatTemplates.saveAllAndFlush(noLayout.seats());
        postTrip(noLayout.admin().token(), noLayout.operatorRoute().getId(), noLayout.bus().getId(), DEPARTURE)
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("INVALID_BUS_TYPE"));
    }

    @Test
    void invalidTimePayloadAndInvalidRouteOffsetsLeaveNoAggregate() throws Exception {
        Fixture f = fixture();
        long tripCount = trips.count();
        long stopCount = tripStops.count();
        long segmentCount = segments.count();
        long seatCount = tripSeats.count();
        long inventoryCount = inventory.count();
        mvc.perform(post("/api/v1/operator/trips").header("Authorization", bearer(f.admin().token()))
                        .contentType("application/json").content(json.writeValueAsBytes(Map.of(
                                "operatorRouteId", f.operatorRoute().getId(), "busId", f.bus().getId(),
                                "departureTime", "2030-09-20T08:00:00"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/operator/trips").header("Authorization", bearer(f.admin().token()))
                        .contentType("application/json").content(json.writeValueAsBytes(Map.of(
                                "operatorRouteId", f.operatorRoute().getId(), "busId", f.bus().getId(),
                                "departureTime", DEPARTURE.toString(), "arrivalTime", "2040-01-01T00:00:00Z"))))
                .andExpect(status().isBadRequest());
        postTrip(f.admin().token(), f.operatorRoute().getId(), f.bus().getId(), OffsetDateTime.now().minusDays(1))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));

        f.routeStops().get(2).setEstimatedOffsetMinutes(60);
        routeStops.saveAndFlush(f.routeStops().get(2));
        postTrip(f.admin().token(), f.operatorRoute().getId(), f.bus().getId(), DEPARTURE)
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("INVALID_ROUTE"));
        assertThat(trips.count()).isEqualTo(tripCount);
        assertThat(tripStops.count()).isEqualTo(stopCount);
        assertThat(segments.count()).isEqualTo(segmentCount);
        assertThat(tripSeats.count()).isEqualTo(seatCount);
        assertThat(inventory.count()).isEqualTo(inventoryCount);
    }

    @Test
    void overlapIsRejectedBoundaryIsAllowedAndCancelledDoesNotBlock() throws Exception {
        Fixture f = fixture();
        postTrip(f.admin().token(), f.operatorRoute().getId(), f.bus().getId(), DEPARTURE)
                .andExpect(status().isCreated());
        postTrip(f.admin().token(), f.operatorRoute().getId(), f.bus().getId(), DEPARTURE.plusMinutes(30))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("BUS_SCHEDULE_CONFLICT"));
        postTrip(f.admin().token(), f.operatorRoute().getId(), f.bus().getId(), DEPARTURE.plusMinutes(120))
                .andExpect(status().isCreated());

        Fixture cancelled = fixture();
        rawTrip(cancelled, DEPARTURE, TripStatus.CANCELLED);
        postTrip(cancelled.admin().token(), cancelled.operatorRoute().getId(), cancelled.bus().getId(), DEPARTURE.plusMinutes(30))
                .andExpect(status().isCreated());
    }

    @Test
    void inventoryPairUniquenessIsEnforcedByMysql() throws Exception {
        Fixture f = fixture();
        long tripId = data(postTrip(f.admin().token(), f.operatorRoute().getId(), f.bus().getId(), DEPARTURE)
                .andExpect(status().isCreated())).path("id").asLong();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO trip_seat_segment_inventory
                    (trip_seat_id,trip_segment_id,status,hold_token,held_by_user_id,hold_expires_at,updated_at,version)
                SELECT i.trip_seat_id,i.trip_segment_id,'AVAILABLE',NULL,NULL,NULL,UTC_TIMESTAMP(6),0
                FROM trip_seat_segment_inventory i
                JOIN trip_seats s ON s.id=i.trip_seat_id
                WHERE s.trip_id=? LIMIT 1
                """, tripId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void operatorCannotRetrieveForeignTripAndListIsScoped() throws Exception {
        Fixture a = fixture();
        Fixture b = fixture();
        long tripId = data(postTrip(a.admin().token(), a.operatorRoute().getId(), a.bus().getId(), DEPARTURE)
                .andExpect(status().isCreated())).path("id").asLong();
        mvc.perform(get("/api/v1/operator/trips/{id}", tripId).header("Authorization", bearer(b.admin().token())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("TRIP_NOT_FOUND"));
        getTrips(b.admin().token()).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
        getTrips(a.admin().token()).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].id").value(tripId));
    }

    private ResultActions getTrips(String token) throws Exception {
        return mvc.perform(get("/api/v1/operator/trips").header("Authorization", bearer(token)));
    }
    private ResultActions postTrip(String token, long operatorRouteId, long busId, OffsetDateTime departure) throws Exception {
        return mvc.perform(post("/api/v1/operator/trips").header("Authorization", bearer(token))
                .contentType("application/json").content(json.writeValueAsBytes(Map.of(
                        "operatorRouteId", operatorRouteId, "busId", busId, "departureTime", departure.toString()))));
    }
    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray()).path("data");
    }
    private static String bearer(String token) { return "Bearer " + token; }

    private Fixture fixture() {
        Admin admin = admin();
        List<Location> points = List.of(location("Origin"), location("Middle"), location("Destination"));
        Route route = new Route();
        route.setName("M4 Route " + UUID.randomUUID());
        route.setOriginLocation(points.get(0)); route.setDestinationLocation(points.get(2));
        route.setEstimatedDistanceKm(new BigDecimal("200.00")); route.setEstimatedDurationMinutes(999);
        route.setStatus(RouteStatus.ACTIVE); routes.saveAndFlush(route);
        List<RouteStop> sourceStops = List.of(
                routeStop(route, points.get(0), 1, 0, true, false),
                routeStop(route, points.get(1), 2, 60, true, true),
                routeStop(route, points.get(2), 3, 120, false, true));
        OperatorRoute association = new OperatorRoute();
        association.setOperator(admin.operator()); association.setRoute(route); association.setStatus(ActiveStatus.ACTIVE);
        operatorRoutes.saveAndFlush(association);
        BusType type = new BusType();
        type.setName("M4 Type " + UUID.randomUUID()); type.setSeatCount(2); type.setStatus(ActiveStatus.ACTIVE);
        busTypes.saveAndFlush(type);
        List<SeatTemplate> sourceSeats = List.of(seat(type, "A01", 1), seat(type, "A02", 2));
        Bus bus = new Bus();
        bus.setOperator(admin.operator()); bus.setBusType(type);
        bus.setLicensePlate("M4-" + UUID.randomUUID().toString().substring(0, 16));
        bus.setStatus(BusStatus.AVAILABLE); buses.saveAndFlush(bus);
        return new Fixture(admin, route, sourceStops, association, type, sourceSeats, bus);
    }

    private Admin admin() {
        TransportOperator operator = new TransportOperator();
        operator.setName("M4 Operator " + UUID.randomUUID()); operator.setCode(UUID.randomUUID().toString());
        operator.setStatus(OperatorStatus.ACTIVE); operators.saveAndFlush(operator);
        User user = account(RoleCode.OPERATOR_ADMIN, operator);
        return new Admin(operator, token(user));
    }
    private User account(RoleCode roleCode, TransportOperator operator) {
        User user = new User();
        user.setFullName("M4 user"); user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPhone("0901234567"); user.setPasswordHash("not-used"); user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);
        userRoles.saveAndFlush(new UserRole(user, roles.findByCode(roleCode).orElseThrow()));
        if (operator != null) {
            OperatorStaff membership = new OperatorStaff();
            membership.setOperator(operator); membership.setUser(user); membership.setStaffCode(UUID.randomUUID().toString());
            membership.setStatus(ActiveStatus.ACTIVE); staff.saveAndFlush(membership);
        }
        return user;
    }
    private String token(User user) {
        return jwt.issue(new CurrentUser(user.getId(), userRoles.findRoleCodesByUserId(user.getId())), "access");
    }
    private Location location(String prefix) {
        Location location = new Location();
        location.setName(prefix + " " + UUID.randomUUID()); location.setProvince("Test"); location.setDistrict("Test");
        location.setStatus(ActiveStatus.ACTIVE); return locations.saveAndFlush(location);
    }
    private RouteStop routeStop(Route route, Location location, int order, int offset, boolean pickup, boolean dropoff) {
        RouteStop stop = new RouteStop();
        stop.setRoute(route); stop.setLocation(location); stop.setStopOrder(order); stop.setEstimatedOffsetMinutes(offset);
        stop.setAllowPickup(pickup); stop.setAllowDropoff(dropoff); stop.setStatus(ActiveStatus.ACTIVE);
        return routeStops.saveAndFlush(stop);
    }
    private SeatTemplate seat(BusType type, String code, int column) {
        SeatTemplate seat = new SeatTemplate();
        seat.setBusType(type); seat.setSeatCode(code); seat.setRow(1); seat.setColumn(column); seat.setFloor(1);
        seat.setSeatType(SeatType.STANDARD); seat.setActive(true); return seatTemplates.saveAndFlush(seat);
    }
    private Trip rawTrip(Fixture f, OffsetDateTime departure, TripStatus status) {
        Trip trip = new Trip();
        trip.setOperatorRoute(f.operatorRoute()); trip.setBus(f.bus());
        LocalDateTime start = LocalDateTime.ofInstant(departure.toInstant(), ZoneOffset.UTC);
        trip.setDepartureTime(start); trip.setEstimatedArrivalTime(start.plusMinutes(120)); trip.setStatus(status);
        return trips.saveAndFlush(trip);
    }

    private record Admin(TransportOperator operator, String token) {}
    private record Fixture(Admin admin, Route route, List<RouteStop> routeStops,
            OperatorRoute operatorRoute, BusType busType, List<SeatTemplate> seats, Bus bus) {}
}
