package com.busgo.hold;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class SeatHoldDtos {
    private SeatHoldDtos() {}

    public record CreateSeatHoldRequest(
            @NotNull @Positive Long tripId,
            @NotNull @Positive Long pickupLocationId,
            @NotNull @Positive Long dropoffLocationId,
            @NotEmpty List<@NotNull @Positive Long> tripSeatIds) {}

    public record Pickup(Long tripStopId, Long locationId, String name,
            OffsetDateTime departureTime) {}

    public record Dropoff(Long tripStopId, Long locationId, String name,
            OffsetDateTime arrivalTime) {}

    public record HeldSeat(Long tripSeatId, String seatCode) {}

    public enum HoldStatus { ACTIVE, EXPIRED }

    public record SeatHoldResponse(String holdToken, Long tripId, Pickup pickup,
            Dropoff dropoff, List<Long> tripSeatIds, List<HeldSeat> seats,
            BigDecimal pricePerSeat, BigDecimal totalPrice, OffsetDateTime expiresAt,
            HoldStatus status) {}
}
