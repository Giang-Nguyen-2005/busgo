package com.busgo;

import com.busgo.auth.AuthDtos.*;
import com.busgo.auth.AuthService;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.security.*;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.hold.SeatHoldDtos.*;
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
import org.springframework.beans.factory.annotation.Autowired;

abstract class M8BookingTestSupport extends JwtTestSupport {
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired SeatHoldService holds;
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

    UserAuth customer(String prefix) {
        Registration registration = auth.register(new RegisterRequest(prefix,
                prefix + UUID.randomUUID() + "@example.test",
                "08" + UUID.randomUUID().toString().replace("-", "").substring(0, 9),
                "test password"));
        CurrentUser user = new CurrentUser(registration.id(), List.of(RoleCode.CUSTOMER));
        return new UserAuth(user, jwt.issue(user, "access"));
    }

    Fixture fixture() {
        List<Location> points = List.of(location("A"), location("B"), location("C"), location("D"));
        Route route = new Route(); route.setName("M8 " + UUID.randomUUID());
        route.setOriginLocation(points.get(0)); route.setDestinationLocation(points.get(3));
        route.setEstimatedDistanceKm(new BigDecimal("300")); route.setEstimatedDurationMinutes(180);
        route.setStatus(RouteStatus.ACTIVE); routes.saveAndFlush(route);
        List<RouteStop> masters = new ArrayList<>();
        for (int i = 0; i < 4; i++) masters.add(master(route, points.get(i), i + 1, i * 60));
        TransportOperator operator = new TransportOperator(); operator.setName("M8 " + UUID.randomUUID());
        operator.setCode(UUID.randomUUID().toString()); operator.setStatus(OperatorStatus.ACTIVE);
        operators.saveAndFlush(operator);
        OperatorRoute association = new OperatorRoute(); association.setOperator(operator);
        association.setRoute(route); association.setStatus(ActiveStatus.ACTIVE);
        operatorRoutes.saveAndFlush(association);
        BusType type = new BusType(); type.setName("M8 " + UUID.randomUUID());
        type.setSeatCount(2); type.setStatus(ActiveStatus.ACTIVE); busTypes.saveAndFlush(type);
        List<SeatTemplate> templates = List.of(template(type, "A01", 1), template(type, "A02", 2));
        Bus bus = new Bus(); bus.setOperator(operator); bus.setBusType(type);
        bus.setLicensePlate("M8-" + UUID.randomUUID().toString().substring(0, 15));
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
        List<OperatorRouteFare> generatedFares = new ArrayList<>();
        for (int from = 0; from < 3; from++) for (int to = from + 1; to < 4; to++) {
            OperatorRouteFare fare = new OperatorRouteFare(); fare.setOperatorRoute(association);
            fare.setFromRouteStop(masters.get(from)); fare.setToRouteStop(masters.get(to));
            fare.setPrice(BigDecimal.valueOf((to - from) * 100L)); fare.setStatus(ActiveStatus.ACTIVE);
            generatedFares.add(fares.saveAndFlush(fare));
        }
        return new Fixture(points, trip, snapshots, generatedSegments, generatedSeats,
                association, operator, route, bus, type, generatedFares);
    }

    SeatHoldResponse hold(UserAuth owner, Fixture fixture, int pickup, int dropoff, int... seatIndexes) {
        List<Long> seatIds = Arrays.stream(seatIndexes)
                .mapToObj(index -> fixture.seats().get(index).getId()).toList();
        return holds.create(owner.user(), new CreateSeatHoldRequest(fixture.trip().getId(),
                fixture.locations().get(pickup).getId(), fixture.locations().get(dropoff).getId(),
                seatIds));
    }

    String bookingBody(String token) {
        return """
                {"holdToken":"%s","contactName":"Nguyen Giang",
                 "contactPhone":"0901234567","contactEmail":"GIANG@example.test"}
                """.formatted(token);
    }

    private Location location(String name) {
        Location value = new Location(); value.setName(name + UUID.randomUUID()); value.setProvince("Test");
        value.setDistrict("Test"); value.setStatus(ActiveStatus.ACTIVE); return locations.saveAndFlush(value);
    }

    private RouteStop master(Route route, Location location, int order, int offset) {
        RouteStop value = new RouteStop(); value.setRoute(route); value.setLocation(location);
        value.setStopOrder(order); value.setEstimatedOffsetMinutes(offset); value.setAllowPickup(order < 4);
        value.setAllowDropoff(order > 1); value.setStatus(ActiveStatus.ACTIVE);
        return routeStops.saveAndFlush(value);
    }

    private SeatTemplate template(BusType type, String code, int column) {
        SeatTemplate value = new SeatTemplate(); value.setBusType(type); value.setSeatCode(code);
        value.setRow(1); value.setColumn(column); value.setFloor(1); value.setSeatType(SeatType.STANDARD);
        value.setActive(true); return seatTemplates.saveAndFlush(value);
    }

    record UserAuth(CurrentUser user, String token) {}
    record Fixture(List<Location> locations, Trip trip, List<TripStop> stops,
            List<TripSegment> segments, List<TripSeat> seats, OperatorRoute operatorRoute,
            TransportOperator operator, Route route, Bus bus, BusType busType,
            List<OperatorRouteFare> fares) {}
}
