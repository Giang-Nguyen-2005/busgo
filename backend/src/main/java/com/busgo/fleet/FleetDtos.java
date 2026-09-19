package com.busgo.fleet;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.fleet.entity.BusStatus;
import com.busgo.fleet.entity.SeatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class FleetDtos {
    private FleetDtos() {}

    public record SeatTemplateResponse(Long id, String seatCode, Integer row, Integer column,
            Integer floor, SeatType seatType, boolean active) {}

    public record BusTypeResponse(Long id, String name, Integer seatCount, String description,
            ActiveStatus status, List<SeatTemplateResponse> seats) {}

    public record CreateBusRequest(
            @NotBlank @Size(max = 30) String licensePlate,
            @NotNull @Positive Long busTypeId) {}

    public record UpdateBusRequest(
            @Size(min = 1, max = 30) String licensePlate,
            @Positive Long busTypeId,
            BusStatus status) {}

    public record BusResponse(Long id, String licensePlate, BusStatus status,
            BusTypeSummary busType) {}

    public record BusTypeSummary(Long id, String name, Integer seatCount) {}
}
