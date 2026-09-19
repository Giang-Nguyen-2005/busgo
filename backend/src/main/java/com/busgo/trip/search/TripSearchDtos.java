package com.busgo.trip.search;

import com.busgo.trip.entity.TripStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class TripSearchDtos {
    private TripSearchDtos() {}

    public enum SearchSort {
        PRICE_ASC, PRICE_DESC, DEPARTURE_ASC, DEPARTURE_DESC
    }

    public record OperatorSummary(Long id, String name) {}
    public record RouteSummary(Long id, String name) {}
    public record BusTypeSummary(Long id, String name) {}
    public record PickupSummary(Long tripStopId, Long locationId, String name,
            OffsetDateTime departureTime) {}
    public record DropoffSummary(Long tripStopId, Long locationId, String name,
            OffsetDateTime arrivalTime) {}

    public record SearchResult(Long tripId, OperatorSummary operator, RouteSummary route,
            BusTypeSummary busType, PickupSummary pickup, DropoffSummary dropoff,
            long durationMinutes, BigDecimal price, long availableSeats, TripStatus status) {}

    public record CustomerTripStop(Long tripStopId, Long locationId, String name, Integer stopOrder,
            boolean allowPickup, boolean allowDropoff, OffsetDateTime arrivalTime,
            OffsetDateTime departureTime) {}

    public record CustomerTripDetail(Long tripId, OperatorSummary operator, RouteSummary route,
            BusTypeSummary busType, PickupSummary pickup, DropoffSummary dropoff,
            long durationMinutes, BigDecimal price, long availableSeats, TripStatus status,
            List<CustomerTripStop> stops) {}
}
