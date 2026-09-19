package com.busgo.trip;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.fleet.entity.SeatType;
import com.busgo.trip.entity.TripStatus;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public final class TripDtos {
    private TripDtos() {}

    public static final class CreateTripRequest {
        @NotNull @Positive
        private final Long operatorRouteId;
        @NotNull @Positive
        private final Long busId;
        @NotNull @Future
        private final OffsetDateTime departureTime;

        @JsonCreator
        public CreateTripRequest(
                @JsonProperty("operatorRouteId") Long operatorRouteId,
                @JsonProperty("busId") Long busId,
                @JsonProperty("departureTime") OffsetDateTime departureTime) {
            this.operatorRouteId = operatorRouteId;
            this.busId = busId;
            this.departureTime = departureTime;
        }

        public Long operatorRouteId() { return operatorRouteId; }
        public Long busId() { return busId; }
        public OffsetDateTime departureTime() { return departureTime; }

        @JsonAnySetter
        void rejectUnknownField(String name, Object ignored) {
            throw new IllegalArgumentException("Unknown trip creation field: " + name);
        }
    }

    public record TripRouteSummary(Long operatorRouteId, Long routeId, String name) {}
    public record TripBusSummary(Long id, String licensePlate, Long busTypeId, String busTypeName) {}

    public record TripSummaryResponse(Long id, TripStatus status, TripRouteSummary route,
            TripBusSummary bus, OffsetDateTime departureTime, OffsetDateTime estimatedArrivalTime,
            long seatCount, long segmentCount) {}

    public record TripStopResponse(Long id, Long sourceRouteStopId, Long locationId, String locationName,
            Integer stopOrder, OffsetDateTime plannedArrivalTime, OffsetDateTime plannedDepartureTime,
            boolean allowPickup, boolean allowDropoff, ActiveStatus status) {}

    public record TripSegmentResponse(Long id, Integer segmentOrder,
            Long fromTripStopId, Long toTripStopId) {}

    public record TripSeatResponse(Long id, Long sourceSeatTemplateId, String seatCode,
            Integer row, Integer column, Integer floor, SeatType seatType) {}

    public record TripDetailResponse(Long id, TripStatus status, TripRouteSummary route,
            TripBusSummary bus, OffsetDateTime departureTime, OffsetDateTime estimatedArrivalTime,
            List<TripStopResponse> stops, List<TripSegmentResponse> segments,
            List<TripSeatResponse> seats) {}
}
