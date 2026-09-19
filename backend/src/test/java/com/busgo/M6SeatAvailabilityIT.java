package com.busgo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.location.entity.Location;
import com.busgo.location.repository.LocationRepository;
import com.busgo.operator.entity.*;
import com.busgo.operator.repository.TransportOperatorRepository;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
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
class M6SeatAvailabilityIT extends JwtTestSupport {
    private static final ZonedDateTime DEPARTURE =
            ZonedDateTime.of(2030, 9, 20, 8, 0, 0, 0, ZoneId.of("Asia/Ho_Chi_Minh"));

    @Autowired MockMvc mvc;
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
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test
    void samePhysicalSeatIsEvaluatedAcrossExactlyTheSelectedSegments() throws Exception {
        Fixture f = fixture();
        setStatus(f, 0, 0, InventoryStatus.BOOKED);
        setStatus(f, 1, 2, InventoryStatus.BOOKED);
        setStatus(f, 2, 1, InventoryStatus.HELD);
        setStatus(f, 3, 2, InventoryStatus.BLOCKED);

        seats(f, 1, 2).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(3))
                .andExpect(jsonPath("data.seats[0].available").value(true))
                .andExpect(jsonPath("data.seats[1].available").value(true))
                .andExpect(jsonPath("data.seats[2].available").value(false))
                .andExpect(jsonPath("data.seats[3].available").value(true));
        seats(f, 1, 3).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(1))
                .andExpect(jsonPath("data.seats[0].available").value(true))
                .andExpect(jsonPath("data.seats[1].available").value(false))
                .andExpect(jsonPath("data.seats[2].available").value(false))
                .andExpect(jsonPath("data.seats[3].available").value(false));
        seats(f, 0, 2).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(2))
                .andExpect(jsonPath("data.seats[0].available").value(false))
                .andExpect(jsonPath("data.seats[1].available").value(true))
                .andExpect(jsonPath("data.seats[2].available").value(false))
                .andExpect(jsonPath("data.seats[3].available").value(true));
        seats(f, 0, 3).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(0))
                .andExpect(jsonPath("data.seats[0].available").value(false))
                .andExpect(jsonPath("data.seats[1].available").value(false))
                .andExpect(jsonPath("data.seats[2].available").value(false))
                .andExpect(jsonPath("data.seats[3].available").value(false));
    }

    @Test
    void bookedHeldAndBlockedOutsideTheJourneyDoNotAffectAvailability() throws Exception {
        Fixture f = fixture();
        setStatus(f, 0, 0, InventoryStatus.BOOKED);
        setStatus(f, 1, 2, InventoryStatus.HELD);
        setStatus(f, 2, 0, InventoryStatus.BLOCKED);

        seats(f, 1, 2).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(4))
                .andExpect(jsonPath("data.seats[0].available").value(true))
                .andExpect(jsonPath("data.seats[1].available").value(true))
                .andExpect(jsonPath("data.seats[2].available").value(true))
                .andExpect(jsonPath("data.seats[3].available").value(true));
    }

    @Test
    void responseUsesTripSeatSnapshotOrderingAndBackendFare() throws Exception {
        Fixture f = fixture();
        SeatTemplate source = f.templates().get(0);
        source.setSeatCode("CHANGED");
        source.setRow(99);
        seatTemplates.saveAndFlush(source);
        em.clear();

        seats(f, 1, 3).andExpect(status().isOk())
                .andExpect(jsonPath("data.tripId").value(f.trip().getId()))
                .andExpect(jsonPath("data.pickup.tripStopId").value(f.stops().get(1).getId()))
                .andExpect(jsonPath("data.pickup.locationId").value(f.points().get(1).getId()))
                .andExpect(jsonPath("data.dropoff.tripStopId").value(f.stops().get(3).getId()))
                .andExpect(jsonPath("data.price").value(200.00))
                .andExpect(jsonPath("data.availableSeatCount").value(4))
                .andExpect(jsonPath("data.seats[0].seatCode").value("A01"))
                .andExpect(jsonPath("data.seats[0].row").value(1))
                .andExpect(jsonPath("data.seats[1].seatCode").value("A02"))
                .andExpect(jsonPath("data.seats[2].seatCode").value("B01"))
                .andExpect(jsonPath("data.seats[3].seatCode").value("C01"))
                .andExpect(jsonPath("data.seats[0].status").doesNotExist());
    }

    @Test
    void requestAndJourneyValidationUseExistingErrors() throws Exception {
        Fixture f = fixture();
        mvc.perform(get("/api/v1/trips/{id}/seats", f.trip().getId())
                        .param("dropoffLocationId", id(f.points().get(2))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/trips/{id}/seats", f.trip().getId())
                        .param("pickupLocationId", id(f.points().get(1))))
                .andExpect(status().isBadRequest());
        seatIds(f.trip().getId(), 999999L, f.points().get(2).getId())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_PICKUP_STOP"));
        seatIds(f.trip().getId(), f.points().get(1).getId(), 999999L)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_DROPOFF_STOP"));
        seats(f, 2, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_ROUTE_DIRECTION"));
        seats(f, 1, 1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_ROUTE_DIRECTION"));
        seatIds(999999L, f.points().get(0).getId(), f.points().get(2).getId())
                .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("TRIP_NOT_FOUND"));

        f.stops().get(1).setAllowPickup(false);
        tripStops.saveAndFlush(f.stops().get(1));
        seats(f, 1, 2).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_PICKUP_STOP"));
        f.stops().get(1).setAllowPickup(true);
        f.stops().get(2).setAllowDropoff(false);
        tripStops.saveAllAndFlush(List.of(f.stops().get(1), f.stops().get(2)));
        seats(f, 1, 2).andExpect(status().isBadRequest())
                .andExpect(jsonPath("code").value("INVALID_DROPOFF_STOP"));
    }

    @Test
    void onlyFutureScheduledJourneysAreBookable() throws Exception {
        Fixture f = fixture();
        for (TripStatus tripStatus : List.of(TripStatus.CANCELLED, TripStatus.DEPARTED,
                TripStatus.COMPLETED, TripStatus.BOARDING)) {
            f.trip().setStatus(tripStatus);
            trips.saveAndFlush(f.trip());
            seats(f, 1, 2).andExpect(status().isConflict())
                    .andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
        }
        f.trip().setStatus(TripStatus.SCHEDULED);
        f.stops().get(1).setPlannedDepartureTime(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        tripStops.saveAndFlush(f.stops().get(1));
        seats(f, 1, 2).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
    }

    @Test
    void missingFareOrIncompleteSegmentSnapshotIsNotBookable() throws Exception {
        Fixture missingFare = fixture();
        OperatorRouteFare exact = missingFare.fares().stream()
                .filter(fare -> fare.getFromRouteStop().getId().equals(missingFare.routeStops().get(1).getId())
                        && fare.getToRouteStop().getId().equals(missingFare.routeStops().get(3).getId()))
                .findFirst().orElseThrow();
        exact.setStatus(ActiveStatus.INACTIVE);
        fares.saveAndFlush(exact);
        seats(missingFare, 1, 3).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));

        Fixture incomplete = fixture();
        inventory.deleteAll(incomplete.inventory());
        inventory.flush();
        segments.delete(incomplete.segments().get(1));
        segments.flush();
        seats(incomplete, 1, 3).andExpect(status().isConflict())
                .andExpect(jsonPath("code").value("TRIP_NOT_BOOKABLE"));
    }

    @Test
    void missingInventoryIsUnavailableAndSoldOutReadDoesNotMutate() throws Exception {
        Fixture f = fixture();
        TripSeatSegmentInventory missing = row(f, 0, 1);
        inventory.delete(missing);
        inventory.flush();
        for (int seat = 1; seat < 4; seat++) setStatus(f, seat, 1, InventoryStatus.BOOKED);
        Map<String, Object> before = inventoryState(f.trip().getId());

        seats(f, 1, 2).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(0))
                .andExpect(jsonPath("data.seats[0].available").value(false))
                .andExpect(jsonPath("data.seats[1].available").value(false))
                .andExpect(jsonPath("data.seats[2].available").value(false))
                .andExpect(jsonPath("data.seats[3].available").value(false));
        assertThat(inventoryState(f.trip().getId())).isEqualTo(before);
    }

    @Test
    void m5AndM6CountsAgreeForTheSameInventoryState() throws Exception {
        Fixture f = fixture();
        setStatus(f, 0, 0, InventoryStatus.BOOKED);
        setStatus(f, 1, 2, InventoryStatus.BOOKED);
        setStatus(f, 2, 1, InventoryStatus.HELD);
        setStatus(f, 3, 2, InventoryStatus.BLOCKED);

        mvc.perform(get("/api/v1/trips/search")
                        .param("pickupLocationId", id(f.points().get(1)))
                        .param("dropoffLocationId", id(f.points().get(3)))
                        .param("departureDate", "2030-09-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("data[0].tripId").value(f.trip().getId()))
                .andExpect(jsonPath("data[0].availableSeats").value(1));
        seats(f, 1, 3).andExpect(status().isOk())
                .andExpect(jsonPath("data.availableSeatCount").value(1));
    }

    @Test
    void anonymousSeatMapIsPublicWhileOperatorAndMutationsRemainProtected() throws Exception {
        Fixture f = fixture();
        seats(f, 1, 2).andExpect(status().isOk());
        mvc.perform(get("/api/v1/operator/trips")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/trips/{id}/seats", f.trip().getId()))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions seats(Fixture fixture, int pickup, int dropoff) throws Exception {
        return seatIds(fixture.trip().getId(), fixture.points().get(pickup).getId(),
                fixture.points().get(dropoff).getId());
    }

    private ResultActions seatIds(long tripId, long pickup, long dropoff) throws Exception {
        return mvc.perform(get("/api/v1/trips/{id}/seats", tripId)
                .param("pickupLocationId", Long.toString(pickup))
                .param("dropoffLocationId", Long.toString(dropoff)));
    }

    private Fixture fixture() {
        List<Location> points = List.of(location("A"), location("B"), location("C"), location("D"));
        Route route = new Route();
        route.setName("M6 route " + UUID.randomUUID());
        route.setOriginLocation(points.get(0)); route.setDestinationLocation(points.get(3));
        route.setEstimatedDistanceKm(new BigDecimal("300.00")); route.setEstimatedDurationMinutes(180);
        route.setStatus(RouteStatus.ACTIVE); routes.saveAndFlush(route);
        List<RouteStop> masters = List.of(masterStop(route, points.get(0), 1, 0),
                masterStop(route, points.get(1), 2, 60), masterStop(route, points.get(2), 3, 120),
                masterStop(route, points.get(3), 4, 180));
        TransportOperator operator = new TransportOperator();
        operator.setName("M6 operator " + UUID.randomUUID()); operator.setCode(UUID.randomUUID().toString());
        operator.setStatus(OperatorStatus.ACTIVE); operators.saveAndFlush(operator);
        OperatorRoute association = new OperatorRoute();
        association.setOperator(operator); association.setRoute(route); association.setStatus(ActiveStatus.ACTIVE);
        operatorRoutes.saveAndFlush(association);
        BusType type = new BusType();
        type.setName("M6 type " + UUID.randomUUID()); type.setSeatCount(4); type.setStatus(ActiveStatus.ACTIVE);
        busTypes.saveAndFlush(type);
        List<SeatTemplate> templates = List.of(template(type, "C01", 2, 1, 2),
                template(type, "A02", 1, 2, 1), template(type, "B01", 2, 1, 1),
                template(type, "A01", 1, 1, 1));
        Bus bus = new Bus();
        bus.setOperator(operator); bus.setBusType(type);
        bus.setLicensePlate("M6-" + UUID.randomUUID().toString().substring(0, 16));
        bus.setStatus(BusStatus.AVAILABLE); buses.saveAndFlush(bus);
        LocalDateTime start = LocalDateTime.ofInstant(DEPARTURE.toInstant(), ZoneOffset.UTC);
        Trip trip = new Trip();
        trip.setOperatorRoute(association); trip.setBus(bus); trip.setDepartureTime(start);
        trip.setEstimatedArrivalTime(start.plusMinutes(180)); trip.setStatus(TripStatus.SCHEDULED);
        trips.saveAndFlush(trip);
        List<TripStop> snapshots = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            TripStop stop = new TripStop();
            stop.setTrip(trip); stop.setSourceRouteStop(masters.get(index)); stop.setLocation(points.get(index));
            stop.setStopOrder(index + 1); stop.setAllowPickup(index < 3); stop.setAllowDropoff(index > 0);
            stop.setPlannedArrivalTime(index == 0 ? null : start.plusMinutes(index * 60L));
            stop.setPlannedDepartureTime(index == 3 ? null : start.plusMinutes(index * 60L));
            stop.setStatus(ActiveStatus.ACTIVE); snapshots.add(stop);
        }
        snapshots = tripStops.saveAllAndFlush(snapshots);
        List<TripSegment> generatedSegments = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            TripSegment segment = new TripSegment();
            segment.setTrip(trip); segment.setFromTripStop(snapshots.get(index));
            segment.setToTripStop(snapshots.get(index + 1)); segment.setSegmentOrder(index + 1);
            generatedSegments.add(segment);
        }
        generatedSegments = segments.saveAllAndFlush(generatedSegments);
        List<TripSeat> generatedSeats = new ArrayList<>();
        for (SeatTemplate source : templates) {
            TripSeat seat = new TripSeat();
            seat.setTrip(trip); seat.setSourceSeatTemplate(source); seat.setSeatCode(source.getSeatCode());
            seat.setRow(source.getRow()); seat.setColumn(source.getColumn()); seat.setFloor(source.getFloor());
            seat.setSeatType(source.getSeatType()); generatedSeats.add(seat);
        }
        generatedSeats = tripSeats.saveAllAndFlush(generatedSeats);
        List<TripSeatSegmentInventory> rows = new ArrayList<>();
        for (TripSeat seat : generatedSeats) for (TripSegment segment : generatedSegments) {
            TripSeatSegmentInventory row = new TripSeatSegmentInventory();
            row.setTripSeat(seat); row.setTripSegment(segment); row.setStatus(InventoryStatus.AVAILABLE);
            rows.add(row);
        }
        rows = inventory.saveAllAndFlush(rows);
        List<OperatorRouteFare> exactFares = new ArrayList<>();
        for (int from = 0; from < 3; from++) for (int to = from + 1; to < 4; to++) {
            OperatorRouteFare fare = new OperatorRouteFare();
            fare.setOperatorRoute(association); fare.setFromRouteStop(masters.get(from));
            fare.setToRouteStop(masters.get(to)); fare.setPrice(new BigDecimal((to - from) * 100 + ".00"));
            fare.setStatus(ActiveStatus.ACTIVE); exactFares.add(fares.saveAndFlush(fare));
        }
        em.flush();
        generatedSeats.sort(Comparator.comparing(TripSeat::getFloor).thenComparing(TripSeat::getRow)
                .thenComparing(TripSeat::getColumn).thenComparing(TripSeat::getSeatCode));
        return new Fixture(points, masters, templates, trip, snapshots, generatedSegments,
                generatedSeats, rows, exactFares);
    }

    private Location location(String name) {
        Location location = new Location();
        location.setName(name + " " + UUID.randomUUID()); location.setProvince("Test");
        location.setDistrict("Test"); location.setStatus(ActiveStatus.ACTIVE);
        return locations.saveAndFlush(location);
    }

    private RouteStop masterStop(Route route, Location location, int order, int offset) {
        RouteStop stop = new RouteStop();
        stop.setRoute(route); stop.setLocation(location); stop.setStopOrder(order);
        stop.setEstimatedOffsetMinutes(offset); stop.setAllowPickup(order < 4); stop.setAllowDropoff(order > 1);
        stop.setStatus(ActiveStatus.ACTIVE); return routeStops.saveAndFlush(stop);
    }

    private SeatTemplate template(BusType type, String code, int row, int column, int floor) {
        SeatTemplate seat = new SeatTemplate();
        seat.setBusType(type); seat.setSeatCode(code); seat.setRow(row); seat.setColumn(column);
        seat.setFloor(floor); seat.setSeatType(SeatType.STANDARD); seat.setActive(true);
        return seatTemplates.saveAndFlush(seat);
    }

    private void setStatus(Fixture f, int seat, int segment, InventoryStatus value) {
        TripSeatSegmentInventory row = row(f, seat, segment);
        row.setStatus(value); inventory.saveAndFlush(row);
    }

    private TripSeatSegmentInventory row(Fixture f, int seat, int segment) {
        Long seatId = f.seats().get(seat).getId();
        Long segmentId = f.segments().get(segment).getId();
        return f.inventory().stream().filter(item -> item.getTripSeat().getId().equals(seatId)
                && item.getTripSegment().getId().equals(segmentId)).findFirst().orElseThrow();
    }

    private Map<String, Object> inventoryState(long tripId) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) row_count, SUM(status = 'AVAILABLE') available_count,
                       SUM(version) version_sum, MAX(updated_at) last_update
                FROM trip_seat_segment_inventory inventory
                JOIN trip_seats seat ON seat.id = inventory.trip_seat_id
                WHERE seat.trip_id = ?
                """, tripId);
    }

    private static String id(com.busgo.common.entity.BaseEntity entity) {
        return entity.getId().toString();
    }

    private record Fixture(List<Location> points, List<RouteStop> routeStops,
            List<SeatTemplate> templates, Trip trip, List<TripStop> stops,
            List<TripSegment> segments, List<TripSeat> seats,
            List<TripSeatSegmentInventory> inventory, List<OperatorRouteFare> fares) {}
}
