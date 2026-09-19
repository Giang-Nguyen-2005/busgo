package com.busgo.trip.search;

import static com.busgo.common.time.BusGoTime.utc;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.route.entity.OperatorRouteFare;
import com.busgo.route.repository.OperatorRouteFareRepository;
import com.busgo.trip.entity.*;
import com.busgo.trip.repository.*;
import java.time.Clock;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CustomerJourneyResolver {
    private final TripRepository trips;
    private final TripStopSnapshotRepository stops;
    private final OperatorRouteFareRepository fares;
    private final TripSegmentResolver segmentResolver;
    private final Clock clock;

    public CustomerJourneyResolver(TripRepository trips, TripStopSnapshotRepository stops,
            OperatorRouteFareRepository fares, TripSegmentResolver segmentResolver, Clock clock) {
        this.trips = trips;
        this.stops = stops;
        this.fares = fares;
        this.segmentResolver = segmentResolver;
        this.clock = clock;
    }

    public ResolvedJourney resolve(Long tripId, Long pickupLocationId, Long dropoffLocationId) {
        if (pickupLocationId.equals(dropoffLocationId)) {
            throw invalid("INVALID_ROUTE_DIRECTION", "Pickup and dropoff locations must be different.");
        }
        Trip trip = trips.findPublicById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("TRIP_NOT_FOUND", "Trip was not found."));
        TripStop pickup = stops.findByTripIdAndLocationId(tripId, pickupLocationId)
                .orElseThrow(() -> invalid("INVALID_PICKUP_STOP", "Pickup stop is not part of this trip."));
        TripStop dropoff = stops.findByTripIdAndLocationId(tripId, dropoffLocationId)
                .orElseThrow(() -> invalid("INVALID_DROPOFF_STOP", "Dropoff stop is not part of this trip."));
        if (!pickup.isAllowPickup() || pickup.getPlannedDepartureTime() == null) {
            throw invalid("INVALID_PICKUP_STOP", "Pickup is not allowed at the selected stop.");
        }
        if (!dropoff.isAllowDropoff() || dropoff.getPlannedArrivalTime() == null) {
            throw invalid("INVALID_DROPOFF_STOP", "Dropoff is not allowed at the selected stop.");
        }
        if (pickup.getStopOrder() >= dropoff.getStopOrder()) {
            throw invalid("INVALID_ROUTE_DIRECTION", "Pickup must occur before dropoff.");
        }
        if (trip.getStatus() != TripStatus.SCHEDULED
                || !pickup.getPlannedDepartureTime().isAfter(utc(clock.instant()))) {
            throw notBookable("Trip is not bookable for the selected journey.");
        }
        List<TripSegment> requiredSegments = segmentResolver.resolve(tripId, pickup, dropoff);
        if (pickup.getSourceRouteStop() == null || dropoff.getSourceRouteStop() == null) {
            throw notBookable("The selected journey has no exact active fare.");
        }
        OperatorRouteFare fare = fares.findExactActiveFare(trip.getOperatorRoute().getId(),
                        pickup.getSourceRouteStop().getId(), dropoff.getSourceRouteStop().getId(),
                        ActiveStatus.ACTIVE)
                .filter(item -> item.getPrice() != null && item.getPrice().signum() > 0)
                .orElseThrow(() -> notBookable("The selected journey has no exact active fare."));
        return new ResolvedJourney(trip, pickup, dropoff, requiredSegments, fare);
    }

    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.BAD_REQUEST, null);
    }

    private static BusinessException notBookable(String message) {
        return new BusinessException("TRIP_NOT_BOOKABLE", message, HttpStatus.CONFLICT, null);
    }

    public record ResolvedJourney(Trip trip, TripStop pickup, TripStop dropoff,
            List<TripSegment> requiredSegments, OperatorRouteFare fare) {
        public int requiredSegmentCount() {
            return requiredSegments.size();
        }
    }
}
