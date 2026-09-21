package com.busgo.trip;

import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.common.response.PagedResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.entity.Bus;
import com.busgo.operator.OperatorContextService;
import com.busgo.trip.TripDtos.*;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {
    private final TripRepository trips;
    private final TripStopSnapshotRepository tripStops;
    private final TripSegmentRepository segments;
    private final TripSeatRepository tripSeats;
    private final OperatorContextService context;
    private final TripAggregateCreator aggregateCreator;

    public TripService(TripRepository trips, TripStopSnapshotRepository tripStops,
            TripSegmentRepository segments, TripSeatRepository tripSeats,
            OperatorContextService context, TripAggregateCreator aggregateCreator) {
        this.trips = trips;
        this.tripStops = tripStops;
        this.segments = segments;
        this.tripSeats = tripSeats;
        this.context = context;
        this.aggregateCreator = aggregateCreator;
    }

    @Transactional
    public TripSummaryResponse create(CurrentUser user, CreateTripRequest request) {
        var operator = context.requireAdminOperator(user);
        LocalDateTime departure = utc(request.departureTime());
        var created = aggregateCreator.create(operator.getId(), request.operatorRouteId(),
                request.busId(), departure);
        return summary(created.trip(), created.seatCount(), created.segmentCount());
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

}
