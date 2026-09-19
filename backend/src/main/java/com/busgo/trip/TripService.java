package com.busgo.trip;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.*;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.BusTypeService;
import com.busgo.fleet.entity.*;
import com.busgo.fleet.repository.*;
import com.busgo.operator.OperatorContextService;
import com.busgo.route.OperatorRouteService;
import com.busgo.route.RouteDefinitionService;
import com.busgo.route.entity.*;
import com.busgo.route.repository.RouteStopRepository;
import com.busgo.trip.TripDtos.*;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {
    private final TripRepository trips;
    private final TripStopSnapshotRepository tripStops;
    private final TripSegmentRepository segments;
    private final TripSeatRepository tripSeats;
    private final TripSeatSegmentInventoryRepository inventory;
    private final RouteStopRepository routeStops;
    private final SeatTemplateRepository seatTemplates;
    private final BusRepository buses;
    private final OperatorContextService context;
    private final OperatorRouteService operatorRoutes;
    private final RouteDefinitionService routeDefinitions;
    private final BusTypeService busTypes;
    private final Clock clock;

    public TripService(TripRepository trips, TripStopSnapshotRepository tripStops,
            TripSegmentRepository segments, TripSeatRepository tripSeats,
            TripSeatSegmentInventoryRepository inventory, RouteStopRepository routeStops,
            SeatTemplateRepository seatTemplates, BusRepository buses,
            OperatorContextService context, OperatorRouteService operatorRoutes,
            RouteDefinitionService routeDefinitions, BusTypeService busTypes, Clock clock) {
        this.trips = trips;
        this.tripStops = tripStops;
        this.segments = segments;
        this.tripSeats = tripSeats;
        this.inventory = inventory;
        this.routeStops = routeStops;
        this.seatTemplates = seatTemplates;
        this.buses = buses;
        this.context = context;
        this.operatorRoutes = operatorRoutes;
        this.routeDefinitions = routeDefinitions;
        this.busTypes = busTypes;
        this.clock = clock;
    }

    @Transactional
    public TripSummaryResponse create(CurrentUser user, CreateTripRequest request) {
        var operator = context.requireAdminOperator(user);
        OperatorRoute association = operatorRoutes.owned(request.operatorRouteId(), operator.getId());
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

        Bus bus = buses.findOwnedByIdForUpdate(request.busId(), operator.getId())
                .orElseThrow(() -> new ResourceNotFoundException("BUS_NOT_FOUND", "Bus was not found."));
        if (bus.getStatus() != BusStatus.AVAILABLE) {
            throw conflict("BUS_NOT_AVAILABLE", "Bus must be AVAILABLE to create a trip.");
        }
        BusType busType = busTypes.requireUsable(bus.getBusType().getId());
        List<SeatTemplate> sourceSeats = seatTemplates
                .findByBusTypeIdAndActiveTrueOrderByFloorAscRowAscColumnAsc(busType.getId());

        LocalDateTime departure = utc(request.departureTime());
        if (!request.departureTime().toInstant().isAfter(clock.instant())) {
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

        return summary(trip, seatSnapshots.size(), generatedSegments.size());
    }

    @Transactional(readOnly = true)
    public PagedResponse<TripSummaryResponse> list(CurrentUser user, LocalDate date, Long routeId,
            Long busId, TripStatus status, int page, int size) {
        Long operatorId = context.requireAdminOperator(user).getId();
        LocalDateTime start = date == null ? null : date.atStartOfDay();
        LocalDateTime end = date == null ? null : date.plusDays(1).atStartOfDay();
        var result = trips.searchOwned(operatorId, start, end, routeId, busId, status,
                PageRequest.of(page, size, Sort.by("departureTime").ascending().and(Sort.by("id"))));
        return PagedResponse.from(result.map(trip -> summary(trip,
                tripSeats.countByTripId(trip.getId()), segments.countByTripId(trip.getId()))));
    }

    @Transactional(readOnly = true)
    public TripDetailResponse get(CurrentUser user, Long tripId) {
        Long operatorId = context.requireAdminOperator(user).getId();
        Trip trip = trips.findOwnedById(tripId, operatorId)
                .orElseThrow(() -> new ResourceNotFoundException("TRIP_NOT_FOUND", "Trip was not found."));
        List<TripStopResponse> stopResponses = tripStops.findByTripIdOrderByStopOrderAsc(tripId).stream()
                .map(this::stopResponse).toList();
        List<TripSegmentResponse> segmentResponses = segments.findByTripIdOrderBySegmentOrderAsc(tripId).stream()
                .map(segment -> new TripSegmentResponse(segment.getId(), segment.getSegmentOrder(),
                        segment.getFromTripStop().getId(), segment.getToTripStop().getId()))
                .toList();
        List<TripSeatResponse> seatResponses = tripSeats.findByTripIdOrderByFloorAscRowAscColumnAsc(tripId).stream()
                .map(seat -> new TripSeatResponse(seat.getId(), id(seat.getSourceSeatTemplate()),
                        seat.getSeatCode(), seat.getRow(), seat.getColumn(), seat.getFloor(), seat.getSeatType()))
                .toList();
        return new TripDetailResponse(trip.getId(), trip.getStatus(), route(trip), bus(trip),
                api(trip.getDepartureTime()), api(trip.getEstimatedArrivalTime()),
                stopResponses, segmentResponses, seatResponses);
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
                row.setHoldToken(null);
                row.setHeldByUser(null);
                row.setHoldExpiresAt(null);
                rows.add(row);
            }
        }
        inventory.saveAllAndFlush(rows);
    }

    private TripSummaryResponse summary(Trip trip, long seatCount, long segmentCount) {
        return new TripSummaryResponse(trip.getId(), trip.getStatus(), route(trip), bus(trip),
                api(trip.getDepartureTime()), api(trip.getEstimatedArrivalTime()), seatCount, segmentCount);
    }

    private TripRouteSummary route(Trip trip) {
        var association = trip.getOperatorRoute();
        return new TripRouteSummary(association.getId(), association.getRoute().getId(),
                association.getRoute().getName());
    }

    private TripBusSummary bus(Trip trip) {
        Bus bus = trip.getBus();
        return new TripBusSummary(bus.getId(), bus.getLicensePlate(), bus.getBusType().getId(),
                bus.getBusType().getName());
    }

    private TripStopResponse stopResponse(TripStop stop) {
        return new TripStopResponse(stop.getId(), id(stop.getSourceRouteStop()), stop.getLocation().getId(),
                stop.getLocation().getName(), stop.getStopOrder(), api(stop.getPlannedArrivalTime()),
                api(stop.getPlannedDepartureTime()), stop.isAllowPickup(), stop.isAllowDropoff(), stop.getStatus());
    }

    private static Long id(com.busgo.common.entity.BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }

    private static LocalDateTime utc(OffsetDateTime value) {
        return LocalDateTime.ofInstant(value.toInstant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static OffsetDateTime api(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT, null);
    }
}
