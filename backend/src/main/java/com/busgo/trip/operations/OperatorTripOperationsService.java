package com.busgo.trip.operations;

import static com.busgo.common.time.BusGoTime.api;
import static com.busgo.trip.operations.OperatorTripOperationsDtos.*;

import com.busgo.common.exception.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.TripRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperatorTripOperationsService {
    private final TripRepository trips;
    private final OperatorContextService context;
    private final OperatorOccupancyQueryRepository queries;

    public OperatorTripOperationsService(TripRepository trips, OperatorContextService context,
            OperatorOccupancyQueryRepository queries) {
        this.trips = trips;
        this.context = context;
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public PassengerManifest manifest(CurrentUser user, Long tripId) {
        Long operatorId = context.requireOperatorMember(user).getId();
        Trip trip = ownedTrip(operatorId, tripId);
        var passengers = queries.manifest(operatorId, tripId).stream().map(row ->
                new PassengerManifestRow(row.bookingId(), row.bookingCode(), row.bookingStatus(),
                        row.bookingItemId(), row.tripSeatId(), row.seatCode(),
                        new Stop(row.pickupId(), row.pickupLocationId(), row.pickupName(),
                                api(row.pickupTime())),
                        new Stop(row.dropoffId(), row.dropoffLocationId(), row.dropoffName(),
                                api(row.dropoffTime())),
                        row.passengerName(), row.ticketPassengerName(),
                        new Contact(row.contactName(), row.contactPhone(), row.contactEmail()),
                        row.paymentStatus(), row.ticketCode())).toList();
        return new PassengerManifest(tripId, trip.getStatus(), passengers);
    }

    @Transactional(readOnly = true)
    public TripOccupancy occupancy(CurrentUser user, Long tripId) {
        Long operatorId = context.requireOperatorMember(user).getId();
        Trip trip = ownedTrip(operatorId, tripId);
        List<OperatorOccupancyQueryRepository.OccupancyRow> rows =
                queries.occupancy(operatorId, tripId);

        LinkedHashMap<Long, List<OperatorOccupancyQueryRepository.OccupancyRow>> bySegment =
                rows.stream().collect(Collectors.groupingBy(
                        OperatorOccupancyQueryRepository.OccupancyRow::tripSegmentId,
                        LinkedHashMap::new, Collectors.toList()));
        var segments = bySegment.values().stream().map(segmentRows -> {
            var first = segmentRows.get(0);
            return new OccupancySegment(first.tripSegmentId(), first.segmentOrder(),
                    first.fromTripStopId(), first.toTripStopId(), first.fromName(), first.toName(),
                    counts(segmentRows));
        }).toList();

        LinkedHashMap<Long, List<OperatorOccupancyQueryRepository.OccupancyRow>> bySeat =
                rows.stream().collect(Collectors.groupingBy(
                        OperatorOccupancyQueryRepository.OccupancyRow::tripSeatId,
                        LinkedHashMap::new, Collectors.toList()));
        var seats = bySeat.values().stream().map(seatRows -> {
            var first = seatRows.get(0);
            var states = seatRows.stream().map(row -> new SeatSegmentState(
                    row.tripSegmentId(), row.segmentOrder(), row.status(),
                    row.status() == InventoryStatus.HELD ? api(row.holdExpiresAt()) : null,
                    row.status() == InventoryStatus.BOOKED ? row.bookingId() : null,
                    row.status() == InventoryStatus.BOOKED ? row.bookingCode() : null,
                    row.status() == InventoryStatus.BOOKED ? row.bookingStatus() : null)).toList();
            return new OccupancySeat(first.tripSeatId(), first.seatCode(), first.row(),
                    first.column(), first.floor(), first.seatType(), states);
        }).toList();
        long wholeTripAvailable = bySeat.values().stream()
                .filter(seatRows -> !seatRows.isEmpty()
                        && seatRows.stream().allMatch(row -> row.status() == InventoryStatus.AVAILABLE))
                .count();
        return new TripOccupancy(tripId, trip.getStatus(), bySeat.size(), bySegment.size(),
                wholeTripAvailable, segments, seats);
    }

    @Transactional
    public TripStatusResponse updateStatus(CurrentUser user, Long tripId, TripStatus requested) {
        Long operatorId = context.requireAdminOperator(user).getId();
        Trip trip = trips.lockOwnedById(tripId, operatorId)
                .orElseThrow(OperatorTripOperationsService::tripNotFound);
        TripStatus current = trip.getStatus();
        if (current == requested) return new TripStatusResponse(tripId, current);
        if (!allowed(current, requested)) throw invalidTransition(current, requested);
        trip.setStatus(requested);
        return new TripStatusResponse(tripId, requested);
    }

    private Trip ownedTrip(Long operatorId, Long tripId) {
        return trips.findOwnedById(tripId, operatorId)
                .orElseThrow(OperatorTripOperationsService::tripNotFound);
    }

    private static boolean allowed(TripStatus current, TripStatus requested) {
        return (current == TripStatus.SCHEDULED && requested == TripStatus.BOARDING)
                || (current == TripStatus.BOARDING && requested == TripStatus.DEPARTED)
                || (current == TripStatus.DEPARTED && requested == TripStatus.COMPLETED);
    }

    private static InventoryCounts counts(
            List<OperatorOccupancyQueryRepository.OccupancyRow> rows) {
        return new InventoryCounts(count(rows, InventoryStatus.AVAILABLE),
                count(rows, InventoryStatus.HELD), count(rows, InventoryStatus.BOOKED),
                count(rows, InventoryStatus.BLOCKED));
    }

    private static long count(List<OperatorOccupancyQueryRepository.OccupancyRow> rows,
            InventoryStatus status) {
        return rows.stream().filter(row -> row.status() == status).count();
    }

    private static ResourceNotFoundException tripNotFound() {
        return new ResourceNotFoundException("TRIP_NOT_FOUND", "Trip was not found.");
    }

    private static BusinessException invalidTransition(TripStatus current, TripStatus requested) {
        return new BusinessException("INVALID_TRIP_STATUS_TRANSITION",
                "Trip status cannot transition from " + current + " to " + requested + ".",
                HttpStatus.CONFLICT, Map.of("currentStatus", current, "requestedStatus", requested));
    }
}
