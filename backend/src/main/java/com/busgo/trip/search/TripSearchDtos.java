package com.busgo.trip.search;

import com.busgo.trip.entity.TripStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class TripSearchDtos {
    private TripSearchDtos() {}

    public enum SearchSort {
        RECOMMENDED, PRICE_ASC, PRICE_DESC, DEPARTURE_ASC, DEPARTURE_DESC, RATING_DESC
    }

    public record OperatorSummary(Long id, String name, Double averageRating, long reviewCount) {
        public OperatorSummary(Long id, String name) { this(id, name, null, 0); }
    }
    public record RouteSummary(Long id, String name) {}
    public record BusTypeSummary(Long id, String name) {}
    public record PickupSummary(Long tripStopId, Long locationId, String name,
            OffsetDateTime departureTime) {}
    public record DropoffSummary(Long tripStopId, Long locationId, String name,
            OffsetDateTime arrivalTime) {}

    public record SearchResult(Long tripId, OperatorSummary operator, RouteSummary route,
            BusTypeSummary busType, String busImageUrl, PickupSummary pickup, DropoffSummary dropoff,
            long durationMinutes, BigDecimal price, long availableSeats, TripStatus status,
            int delayMinutes, String operationalLabel, OffsetDateTime expectedPickupAt, OffsetDateTime expectedDropoffAt) {}

    public record CustomerTripStop(Long tripStopId, Long locationId, String name, Integer stopOrder,
            boolean allowPickup, boolean allowDropoff, OffsetDateTime arrivalTime,
            OffsetDateTime departureTime) {}

    public record CustomerTripDetail(Long tripId, OperatorSummary operator, RouteSummary route,
            BusTypeSummary busType, String busImageUrl, PickupSummary pickup, DropoffSummary dropoff,
            long durationMinutes, BigDecimal price, long availableSeats, TripStatus status,
            List<CustomerTripStop> stops, com.busgo.trip.operations.LiveTripState operations) {}
}
