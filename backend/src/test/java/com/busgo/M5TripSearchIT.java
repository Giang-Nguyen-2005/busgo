package com.busgo;

import static com.busgo.common.time.BusGoTime.BUSINESS_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class M5TripSearchIT extends JwtTestSupport {
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2030, 9, 20);
    private static final ZonedDateTime ORIGIN_DEPARTURE = BUSINESS_DATE
            .atTime(23, 30).atZone(BUSINESS_ZONE);

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
    @Autowired OperatorRouteFareRepository fares;
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
    void fourStopSegmentReuseProducesJourneySpecificAvailability() throws Exception {
        Fixture f = fixture(ORIGIN_DEPARTURE, "Segment operator");

        search(f, 0, 3, BUSINESS_DATE).andExpect(status().isOk())
                .andExpect(jsonPath("data[0].availableSeats").value(2));

        setInventoryStatus(f, 0, 0, InventoryStatus.BOOKED);
        setInventoryStatus(f, 1, 2, InventoryStatus.BOOKED);

        search(f, 1, 2, BUSINESS_DATE.plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("data[0].availableSeats").value(2));
        search(f, 1, 3, BUSINESS_DATE.plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("data[0].availableSeats").value(1));
        search(f, 0, 2, BUSINESS_DATE).andExpect(status().isOk())
                .andExpect(jsonPath("data[0].availableSeats").value(1));
        search(f, 0, 3, BUSINESS_DATE).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
    }

    @Test
    void locationDirectionAndPickupDropoffPermissionsAreEnforced() throws Exception {
        Fixture f = fixture(ORIGIN_DEPARTURE, "Direction operator");
        searchIds(f.locations().get(0).getId(), 999999L, BUSINESS_DATE)
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(0));
        searchIds(999999L, f.locations().get(2).getId(), BUSINESS_DATE)
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(0));
        search(f, 2, 0, BUSINESS_DATE).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));

        f.tripStops().get(1).setAllowPickup(false);
        tripStops.saveAndFlush(f.tripStops().get(1));
        search(f, 1, 2, BUSINESS_DATE.plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
        f.tripStops().get(1).setAllowPickup(true);
        f.tripStops().get(2).setAllowDropoff(false);
        tripStops.saveAllAndFlush(List.of(f.tripStops().get(1), f.tripStops().get(2)));
        search(f, 1, 2, BUSINESS_DATE.plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
    }

    @Test
    void onlyScheduledTripsWithFuturePickupAreSearchable() throws Exception {
        Fixture f = fixture(ORIGIN_DEPARTURE, "Lifecycle operator");
        for (TripStatus tripStatus : List.of(TripStatus.CANCELLED, TripStatus.DEPARTED,
                TripStatus.COMPLETED, TripStatus.BOARDING)) {
            f.trip().setStatus(tripStatus);
            trips.saveAndFlush(f.trip());
            search(f, 0, 2, BUSINESS_DATE).andExpect(status().isOk())
                    .andExpect(jsonPath("data.length()").value(0));
        }

        ZonedDateTime past = ZonedDateTime.now(BUSINESS_ZONE).minusHours(2);
        Fixture old = fixture(past, "Past operator");
        search(old, 0, 2, past.toLocalDate()).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
    }

    @Test
    void businessDateUsesSelectedIntermediatePickupInHoChiMinhTime() throws Exception {
        Fixture f = fixture(ORIGIN_DEPARTURE, "Timezone operator");
        search(f, 0, 2, BUSINESS_DATE).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].pickup.departureTime").value("2030-09-20T16:30:00Z"));
        search(f, 1, 3, BUSINESS_DATE).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
        search(f, 1, 3, BUSINESS_DATE.plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].pickup.departureTime").value("2030-09-20T17:30:00Z"));
    }

    @Test
    void exactActiveFareIsRequiredAndBackendDerived() throws Exception {
        Fixture f = fixture(ORIGIN_DEPARTURE, "Fare operator");
        OperatorRouteFare exact = f.fares().stream()
                .filter(fare -> fare.getFromRouteStop().getId().equals(f.routeStops().get(1).getId())
                        && fare.getToRouteStop().getId().equals(f.routeStops().get(3).getId()))
                .findFirst().orElseThrow();
        search(f, 1, 3, BUSINESS_DATE.plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("data[0].price").value(200.00));

        exact.setStatus(ActiveStatus.INACTIVE);
        fares.saveAndFlush(exact);
        search(f, 1, 3, BUSINESS_DATE.plusDays(1)).andExpect(status().isOk())
                .andExpect(jsonPath("data.length()").value(0));
        detail(f, 1, 3).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
    }

    @Test
    void customerDetailRequiresAndValidatesSelectedJourney() throws Exception {
        Fixture f = fixture(ORIGIN_DEPARTURE, "Detail operator");
        mvc.perform(get("/api/v1/trips/{id}", f.trip().getId()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/trips/{id}", f.trip().getId())
                        .param("pickupLocationId", Long.toString(id(f.locations().get(1)))))
                .andExpect(status().isBadRequest());
        detailIds(f.trip().getId(), 999999L, id(f.locations().get(2)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_PICKUP_STOP"));
        detail(f, 2, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_ROUTE_DIRECTION"));

        detail(f, 1, 3).andExpect(status().isOk())
                .andExpect(jsonPath("data.tripId").value(f.trip().getId()))
                .andExpect(jsonPath("data.pickup.departureTime").value("2030-09-20T17:30:00Z"))
                .andExpect(jsonPath("data.dropoff.arrivalTime").value("2030-09-20T19:30:00Z"))
                .andExpect(jsonPath("data.price").value(200.00))
                .andExpect(jsonPath("data.availableSeats").value(2))
                .andExpect(jsonPath("data.stops.length()").value(4));
        detailIds(999999L, id(f.locations().get(0)), id(f.locations().get(2)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("TRIP_NOT_FOUND"));
    }

    @Test
    void paginationSortingFiltersAndPlatformWideResultsWork() throws Exception {
        Fixture first = fixture(ORIGIN_DEPARTURE, "Operator one");
        TripCopy second = copyTrip(first, ORIGIN_DEPARTURE.plusMinutes(30), "Operator two",
                new BigDecimal("75.00"));
        long firstOperator = first.operatorRoute().getOperator().getId();

        searchIds(id(first.locations().get(1)), id(first.locations().get(2)), BUSINESS_DATE.plusDays(1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data[0].tripId").value(first.trip().getId()))
                .andExpect(jsonPath("data[1].tripId").value(second.tripId()));
        searchIds(id(first.locations().get(1)), id(first.locations().get(2)), BUSINESS_DATE.plusDays(1),
                "size", "1", "sort", "PRICE_ASC")
                .andExpect(status().isOk())
                .andExpect(jsonPath("data[0].tripId").value(second.tripId()))
                .andExpect(jsonPath("pagination.totalElements").value(2))
                .andExpect(jsonPath("pagination.totalPages").value(2));
        searchIds(id(first.locations().get(1)), id(first.locations().get(2)), BUSINESS_DATE.plusDays(1),
                "operatorId", Long.toString(firstOperator))
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].operator.id").value(firstOperator));
        searchIds(id(first.locations().get(1)), id(first.locations().get(2)), BUSINESS_DATE.plusDays(1),
                "minPrice", "90", "maxPrice", "110")
                .andExpect(status().isOk()).andExpect(jsonPath("data.length()").value(1))
                .andExpect(jsonPath("data[0].price").value(100.00));
    }

    @Test
    void publicReadsIncludeSeatMapButDoNotExposeOperatorOrMutationEndpoints() throws Exception {
        Fixture f = fixture(ORIGIN_DEPARTURE, "Security operator");
        search(f, 0, 2, BUSINESS_DATE).andExpect(status().isOk());
        detail(f, 0, 2).andExpect(status().isOk());
        mvc.perform(get("/api/v1/trips/{id}/seats", f.trip().getId())
                        .param("pickupLocationId", Long.toString(id(f.locations().get(0))))
                        .param("dropoffLocationId", Long.toString(id(f.locations().get(2)))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/trips/{id}/seats", f.trip().getId()))
                .andExpect(status().isUnauthorized());

        User customer = account(RoleCode.CUSTOMER);
        String customerToken = jwt.issue(new CurrentUser(customer.getId(), List.of(RoleCode.CUSTOMER)), "access");
        mvc.perform(get("/api/v1/operator/trips").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchNeverMutatesInventory() throws Exception {
        Fixture f = fixture(ORIGIN_DEPARTURE, "Read-only operator");
        Map<String, Object> before = inventoryState(f.trip().getId());
        search(f, 0, 3, BUSINESS_DATE).andExpect(status().isOk());
        assertThat(inventoryState(f.trip().getId())).isEqualTo(before);
    }

    private ResultActions search(Fixture fixture, int pickup, int dropoff, LocalDate date) throws Exception {
        return searchIds(id(fixture.locations().get(pickup)), id(fixture.locations().get(dropoff)), date);
    }

    private ResultActions searchIds(long pickup, long dropoff, LocalDate date, String... extra) throws Exception {
        var request = get("/api/v1/trips/search")
                .param("pickupLocationId", Long.toString(pickup))
                .param("dropoffLocationId", Long.toString(dropoff))
                .param("departureDate", date.toString());
        for (int index = 0; index < extra.length; index += 2) request.param(extra[index], extra[index + 1]);
        return mvc.perform(request);
    }

    private ResultActions detail(Fixture fixture, int pickup, int dropoff) throws Exception {
        return detailIds(fixture.trip().getId(), id(fixture.locations().get(pickup)),
                id(fixture.locations().get(dropoff)));
    }

    private ResultActions detailIds(long tripId, long pickup, long dropoff) throws Exception {
        return mvc.perform(get("/api/v1/trips/{id}", tripId)
                .param("pickupLocationId", Long.toString(pickup))
                .param("dropoffLocationId", Long.toString(dropoff)));
    }

    private Fixture fixture(ZonedDateTime originDeparture, String operatorName) {
        List<Location> points = List.of(location("A"), location("B"), location("C"), location("D"));
        Route route = new Route();
        route.setName("Four-stop " + UUID.randomUUID());
        route.setOriginLocation(points.get(0));
        route.setDestinationLocation(points.get(3));
        route.setEstimatedDistanceKm(new BigDecimal("300.00"));
        route.setEstimatedDurationMinutes(180);
        route.setStatus(RouteStatus.ACTIVE);
        routes.saveAndFlush(route);
        List<RouteStop> masters = List.of(
                masterStop(route, points.get(0), 1, 0), masterStop(route, points.get(1), 2, 60),
                masterStop(route, points.get(2), 3, 120), masterStop(route, points.get(3), 4, 180));
        TransportOperator operator = operator(operatorName);
        OperatorRoute association = operatorRoute(operator, route);
        BusType type = busType();
        Bus bus = bus(operator, type);
        Trip trip = trip(association, bus, originDeparture);
        List<TripStop> snapshots = snapshotStops(trip, masters, points, originDeparture);
        List<TripSegment> generatedSegments = generatedSegments(trip, snapshots);
        List<TripSeat> seats = generatedSeats(trip, type);
        List<TripSeatSegmentInventory> rows = generatedInventory(seats, generatedSegments);
        List<OperatorRouteFare> exactFares = fares(association, masters);
        em.flush();
        return new Fixture(points, route, masters, association, type, trip, snapshots,
                generatedSegments, seats, rows, exactFares);
    }

    private TripCopy copyTrip(Fixture source, ZonedDateTime departure, String name, BigDecimal bToCPrice) {
        TransportOperator operator = operator(name);
        OperatorRoute association = operatorRoute(operator, source.route());
        Bus bus = bus(operator, source.busType());
        Trip trip = trip(association, bus, departure);
        List<TripStop> snapshots = snapshotStops(trip, source.routeStops(), source.locations(), departure);
        List<TripSegment> generatedSegments = generatedSegments(trip, snapshots);
        List<TripSeat> seats = generatedSeats(trip, source.busType());
        generatedInventory(seats, generatedSegments);
        OperatorRouteFare fare = fare(association, source.routeStops().get(1), source.routeStops().get(2), bToCPrice);
        em.flush();
        return new TripCopy(trip.getId(), fare.getId());
    }

    private TransportOperator operator(String name) {
        TransportOperator operator = new TransportOperator();
        operator.setName(name); operator.setCode(UUID.randomUUID().toString());
        operator.setStatus(OperatorStatus.ACTIVE);
        return operators.saveAndFlush(operator);
    }

    private OperatorRoute operatorRoute(TransportOperator operator, Route route) {
        OperatorRoute association = new OperatorRoute();
        association.setOperator(operator); association.setRoute(route); association.setStatus(ActiveStatus.ACTIVE);
        return operatorRoutes.saveAndFlush(association);
    }

    private BusType busType() {
        BusType type = new BusType();
        type.setName("M5 type " + UUID.randomUUID()); type.setSeatCount(2); type.setStatus(ActiveStatus.ACTIVE);
        busTypes.saveAndFlush(type);
        for (int index = 1; index <= 2; index++) {
            SeatTemplate template = new SeatTemplate();
            template.setBusType(type); template.setSeatCode("A0" + index); template.setRow(1);
            template.setColumn(index); template.setFloor(1); template.setSeatType(SeatType.STANDARD);
            template.setActive(true); seatTemplates.saveAndFlush(template);
        }
        return type;
    }

    private Bus bus(TransportOperator operator, BusType type) {
        Bus bus = new Bus();
        bus.setOperator(operator); bus.setBusType(type);
        bus.setLicensePlate("M5-" + UUID.randomUUID().toString().substring(0, 16));
        bus.setStatus(BusStatus.AVAILABLE);
        return buses.saveAndFlush(bus);
    }

    private Trip trip(OperatorRoute association, Bus bus, ZonedDateTime departure) {
        LocalDateTime start = LocalDateTime.ofInstant(departure.toInstant(), ZoneOffset.UTC);
        Trip trip = new Trip();
        trip.setOperatorRoute(association); trip.setBus(bus); trip.setDepartureTime(start);
        trip.setEstimatedArrivalTime(start.plusMinutes(180)); trip.setStatus(TripStatus.SCHEDULED);
        return trips.saveAndFlush(trip);
    }

    private List<TripStop> snapshotStops(Trip trip, List<RouteStop> masters,
            List<Location> points, ZonedDateTime departure) {
        LocalDateTime start = LocalDateTime.ofInstant(departure.toInstant(), ZoneOffset.UTC);
        List<TripStop> result = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            TripStop stop = new TripStop();
            stop.setTrip(trip); stop.setSourceRouteStop(masters.get(index)); stop.setLocation(points.get(index));
            stop.setStopOrder(index + 1); stop.setAllowPickup(index < 3); stop.setAllowDropoff(index > 0);
            stop.setPlannedArrivalTime(index == 0 ? null : start.plusMinutes(index * 60L));
            stop.setPlannedDepartureTime(index == 3 ? null : start.plusMinutes(index * 60L));
            stop.setStatus(ActiveStatus.ACTIVE); result.add(stop);
        }
        return tripStops.saveAllAndFlush(result);
    }

    private List<TripSegment> generatedSegments(Trip trip, List<TripStop> stops) {
        List<TripSegment> result = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            TripSegment segment = new TripSegment();
            segment.setTrip(trip); segment.setFromTripStop(stops.get(index));
            segment.setToTripStop(stops.get(index + 1)); segment.setSegmentOrder(index + 1);
            result.add(segment);
        }
        return segments.saveAllAndFlush(result);
    }

    private List<TripSeat> generatedSeats(Trip trip, BusType type) {
        List<SeatTemplate> templates = seatTemplates.findByBusTypeIdAndActiveTrueOrderByFloorAscRowAscColumnAsc(type.getId());
        List<TripSeat> result = new ArrayList<>();
        for (SeatTemplate template : templates) {
            TripSeat seat = new TripSeat();
            seat.setTrip(trip); seat.setSourceSeatTemplate(template); seat.setSeatCode(template.getSeatCode());
            seat.setRow(template.getRow()); seat.setColumn(template.getColumn()); seat.setFloor(template.getFloor());
            seat.setSeatType(template.getSeatType()); result.add(seat);
        }
        return tripSeats.saveAllAndFlush(result);
    }

    private List<TripSeatSegmentInventory> generatedInventory(List<TripSeat> seats,
            List<TripSegment> tripSegments) {
        List<TripSeatSegmentInventory> rows = new ArrayList<>();
        for (TripSeat seat : seats) for (TripSegment segment : tripSegments) {
            TripSeatSegmentInventory row = new TripSeatSegmentInventory();
            row.setTripSeat(seat); row.setTripSegment(segment); row.setStatus(InventoryStatus.AVAILABLE);
            rows.add(row);
        }
        return inventory.saveAllAndFlush(rows);
    }

    private List<OperatorRouteFare> fares(OperatorRoute association, List<RouteStop> stops) {
        List<OperatorRouteFare> result = new ArrayList<>();
        for (int from = 0; from < 3; from++) for (int to = from + 1; to < 4; to++) {
            result.add(fare(association, stops.get(from), stops.get(to),
                    new BigDecimal((to - from) * 100 + ".00")));
        }
        return result;
    }

    private OperatorRouteFare fare(OperatorRoute association, RouteStop from, RouteStop to, BigDecimal price) {
        OperatorRouteFare fare = new OperatorRouteFare();
        fare.setOperatorRoute(association); fare.setFromRouteStop(from); fare.setToRouteStop(to);
        fare.setPrice(price); fare.setStatus(ActiveStatus.ACTIVE);
        return fares.saveAndFlush(fare);
    }

    private RouteStop masterStop(Route route, Location location, int order, int offset) {
        RouteStop stop = new RouteStop();
        stop.setRoute(route); stop.setLocation(location); stop.setStopOrder(order);
        stop.setEstimatedOffsetMinutes(offset); stop.setAllowPickup(order < 4); stop.setAllowDropoff(order > 1);
        stop.setStatus(ActiveStatus.ACTIVE);
        return routeStops.saveAndFlush(stop);
    }

    private Location location(String prefix) {
        Location location = new Location();
        location.setName(prefix + " " + UUID.randomUUID()); location.setProvince("Test");
        location.setDistrict("Test"); location.setStatus(ActiveStatus.ACTIVE);
        return locations.saveAndFlush(location);
    }

    private void setInventoryStatus(Fixture fixture, int seat, int segment, InventoryStatus value) {
        TripSeatSegmentInventory row = fixture.inventory().stream()
                .filter(item -> item.getTripSeat().getId().equals(fixture.seats().get(seat).getId())
                        && item.getTripSegment().getId().equals(fixture.segments().get(segment).getId()))
                .findFirst().orElseThrow();
        row.setStatus(value); inventory.saveAndFlush(row);
    }

    private User account(RoleCode roleCode) {
        User user = new User();
        user.setFullName("M5 customer"); user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPhone("0901234567"); user.setPasswordHash("not-used"); user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);
        userRoles.saveAndFlush(new UserRole(user, roles.findByCode(roleCode).orElseThrow()));
        return user;
    }

    private Map<String, Object> inventoryState(long tripId) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) row_count,
                       SUM(status = 'AVAILABLE') available_count,
                       SUM(version) version_sum,
                       MAX(updated_at) last_update
                FROM trip_seat_segment_inventory inventory
                JOIN trip_seats seat ON seat.id = inventory.trip_seat_id
                WHERE seat.trip_id = ?
                """, tripId);
    }

    private static long id(com.busgo.common.entity.BaseEntity entity) { return entity.getId(); }

    private record Fixture(List<Location> locations, Route route, List<RouteStop> routeStops,
            OperatorRoute operatorRoute, BusType busType, Trip trip, List<TripStop> tripStops,
            List<TripSegment> segments, List<TripSeat> seats,
            List<TripSeatSegmentInventory> inventory, List<OperatorRouteFare> fares) {}
    private record TripCopy(long tripId, long fareId) {}
}
