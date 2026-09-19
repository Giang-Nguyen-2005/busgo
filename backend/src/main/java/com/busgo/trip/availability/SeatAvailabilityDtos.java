package com.busgo.trip.availability;

import com.busgo.fleet.entity.SeatType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class SeatAvailabilityDtos {
    private SeatAvailabilityDtos() {}

    public record Pickup(Long tripStopId, Long locationId, String name,
            OffsetDateTime departureTime) {}

    public record Dropoff(Long tripStopId, Long locationId, String name,
            OffsetDateTime arrivalTime) {}

    public record Seat(Long tripSeatId, String seatCode, Integer row, Integer column,
            Integer floor, SeatType seatType, boolean available) {}

    public record SeatMap(Long tripId, Pickup pickup, Dropoff dropoff, BigDecimal price,
            long availableSeatCount, List<Seat> seats) {}
}
