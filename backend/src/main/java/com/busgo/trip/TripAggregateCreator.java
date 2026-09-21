package com.busgo.trip;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.fleet.BusTypeService;
import com.busgo.fleet.entity.Bus;
import com.busgo.fleet.entity.BusStatus;
import com.busgo.fleet.entity.SeatTemplate;
import com.busgo.fleet.repository.BusRepository;
import com.busgo.fleet.repository.SeatTemplateRepository;
import com.busgo.route.OperatorRouteService;
import com.busgo.route.RouteDefinitionService;
import com.busgo.route.entity.OperatorRoute;
import com.busgo.route.entity.Route;
import com.busgo.route.entity.RouteStatus;
import com.busgo.route.entity.RouteStop;
import com.busgo.route.repository.RouteStopRepository;
import com.busgo.trip.entity.InventoryStatus;
import com.busgo.trip.entity.Trip;
import com.busgo.trip.entity.TripSeat;
import com.busgo.trip.entity.TripSeatSegmentInventory;
import com.busgo.trip.entity.TripSegment;
import com.busgo.trip.entity.TripStatus;
import com.busgo.trip.entity.TripStop;
import com.busgo.trip.repository.TripRepository;
import com.busgo.trip.repository.TripSeatRepository;
import com.busgo.trip.repository.TripSeatSegmentInventoryRepository;
import com.busgo.trip.repository.TripSegmentRepository;
import com.busgo.trip.repository.TripStopSnapshotRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the complete, immutable trip snapshot and inventory aggregate. */
@Service
public class TripAggregateCreator {
    private final TripRepository trips;
    private final TripStopSnapshotRepository tripStops;
    private final TripSegmentRepository segments;
    private final TripSeatRepository tripSeats;
    private final TripSeatSegmentInventoryRepository inventory;
    private final RouteStopRepository routeStops;
    private final SeatTemplateRepository seatTemplates;
    private final BusRepository buses;
    private final OperatorRouteService operatorRoutes;
    private final RouteDefinitionService routeDefinitions;
    private final BusTypeService busTypes;
    private final Clock clock;

    public TripAggregateCreator(TripRepository trips, TripStopSnapshotRepository tripStops,
            TripSegmentRepository segments, TripSeatRepository tripSeats,
            TripSeatSegmentInventoryRepository inventory, RouteStopRepository routeStops,
            SeatTemplateRepository seatTemplates, BusRepository buses,
            OperatorRouteService operatorRoutes, RouteDefinitionService routeDefinitions,
            BusTypeService busTypes, Clock clock) {
        this.trips = trips;
        this.tripStops = tripStops;
        this.segments = segments;
        this.tripSeats = tripSeats;
        this.inventory = inventory;
        this.routeStops = routeStops;
        this.seatTemplates = seatTemplates;
        this.buses = buses;
        this.operatorRoutes = operatorRoutes;
        this.routeDefinitions = routeDefinitions;
        this.busTypes = busTypes;
        this.clock = clock;
    }

    @Transactional
    public CreatedTrip create(Long operatorId, Long operatorRouteId, Long busId,
            LocalDateTime departure) {
        OperatorRoute association = operatorRoutes.owned(operatorRouteId, operatorId);
        if (association.getStatus() != ActiveStatus.ACTIVE) {
            throw conflict("OPERATOR_ROUTE_INACTIVE", "Operator route is not active.");
        }
        Route route = association.getRoute();
        if (route.getStatus() != RouteStatus.ACTIVE) {
            throw conflict("INVALID_ROUTE", "Underlying route is not active.");
        }
        routeDefinitions.requireUsable(route.getId());
        List<RouteStop> sourceStops = routeStops.findByRouteIdAndStatusOrderByStopOrderAsc(
                route.getId(), ActiveStatus.ACTIVE);

        Bus bus = buses.findOwnedByIdForUpdate(busId, operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("BUS_NOT_FOUND", "Bus was not found."));
        if (bus.getStatus() != BusStatus.AVAILABLE) {
            throw conflict("BUS_NOT_AVAILABLE", "Bus must be AVAILABLE to create a trip.");
        }
        var busType = busTypes.requireUsable(bus.getBusType().getId());
        List<SeatTemplate> sourceSeats = seatTemplates
                .findByBusTypeIdAndActiveTrueOrderByFloorAscRowAscColumnAsc(busType.getId());

        if (!departure.isAfter(LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC))) {
            throw new BusinessException("INVALID_TRIP_TIME", "Departure time must be in the future.",
                    HttpStatus.BAD_REQUEST, null);
        }
        int finalOffset = sourceStops.get(sourceStops.size() - 1).getEstimatedOffsetMinutes();
        LocalDateTime arrival = departure.plusMinutes(finalOffset);
        if (!arrival.isAfter(departure)) {
            throw new BusinessException("INVALID_TRIP_TIME", "Calculated arrival must be after departure.",
                    HttpStatus.BAD_REQUEST, null);
        }
        if (trips.hasScheduleConflict(bus.getId(), departure, arrival)) {
            throw conflict("BUS_SCHEDULE_CONFLICT",
                    "The selected bus is already assigned to another trip during this period.");
        }

        Trip trip = new Trip();
        trip.setOperatorRoute(association);
        trip.setBus(bus);
        trip.setDepartureTime(departure);
        trip.setEstimatedArrivalTime(arrival);
        trip.setStatus(TripStatus.SCHEDULED);
        trips.saveAndFlush(trip);

        List<TripStop> stopSnapshots = snapshotStops(trip, sourceStops, departure);
        List<TripSegment> generatedSegments = generateSegments(trip, stopSnapshots);
        List<TripSeat> seatSnapshots = snapshotSeats(trip, sourceSeats);
        generateInventory(seatSnapshots, generatedSegments);
        return new CreatedTrip(trip, seatSnapshots.size(), generatedSegments.size());
    }

    private List<TripStop> snapshotStops(Trip trip, List<RouteStop> source, LocalDateTime departure) {
        List<TripStop> result = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            RouteStop original = source.get(index);
            LocalDateTime planned = departure.plusMinutes(original.getEstimatedOffsetMinutes());
            TripStop snapshot = new TripStop();
            snapshot.setTrip(trip);
            snapshot.setSourceRouteStop(original);
            snapshot.setLocation(original.getLocation());
            snapshot.setStopOrder(original.getStopOrder());
            snapshot.setPlannedArrivalTime(index == 0 ? null : planned);
            snapshot.setPlannedDepartureTime(index == source.size() - 1 ? null
                    : index == 0 ? departure : planned);
            snapshot.setAllowPickup(original.isAllowPickup());
            snapshot.setAllowDropoff(original.isAllowDropoff());
            snapshot.setStatus(original.getStatus());
            result.add(snapshot);
        }
        return tripStops.saveAllAndFlush(result);
    }

    private List<TripSegment> generateSegments(Trip trip, List<TripStop> stops) {
        List<TripSegment> result = new ArrayList<>();
        for (int index = 0; index < stops.size() - 1; index++) {
            TripSegment segment = new TripSegment();
            segment.setTrip(trip);
            segment.setFromTripStop(stops.get(index));
            segment.setToTripStop(stops.get(index + 1));
            segment.setSegmentOrder(index + 1);
            result.add(segment);
        }
        return segments.saveAllAndFlush(result);
    }

    private List<TripSeat> snapshotSeats(Trip trip, List<SeatTemplate> source) {
        List<TripSeat> result = source.stream().map(original -> {
            TripSeat snapshot = new TripSeat();
            snapshot.setTrip(trip);
            snapshot.setSourceSeatTemplate(original);
            snapshot.setSeatCode(original.getSeatCode());
            snapshot.setRow(original.getRow());
            snapshot.setColumn(original.getColumn());
            snapshot.setFloor(original.getFloor());
            snapshot.setSeatType(original.getSeatType());
            return snapshot;
        }).toList();
        return tripSeats.saveAllAndFlush(result);
    }

    private void generateInventory(List<TripSeat> seats, List<TripSegment> tripSegments) {
        List<TripSeatSegmentInventory> rows = new ArrayList<>(seats.size() * tripSegments.size());
        for (TripSeat seat : seats) {
            for (TripSegment segment : tripSegments) {
                TripSeatSegmentInventory row = new TripSeatSegmentInventory();
                row.setTripSeat(seat);
                row.setTripSegment(segment);
                row.setStatus(InventoryStatus.AVAILABLE);
                rows.add(row);
            }
        }
        inventory.saveAllAndFlush(rows);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT, null);
    }

    public record CreatedTrip(Trip trip, long seatCount, long segmentCount) {}
}
