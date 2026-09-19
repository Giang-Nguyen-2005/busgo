package com.busgo.trip.availability;

import com.busgo.common.response.ApiResponse;
import com.busgo.trip.availability.SeatAvailabilityDtos.SeatMap;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/trips")
public class SeatAvailabilityController {
    private final SeatAvailabilityService service;

    public SeatAvailabilityController(SeatAvailabilityService service) {
        this.service = service;
    }

    @GetMapping("/{tripId}/seats")
    public ApiResponse<SeatMap> get(@PathVariable @Positive Long tripId,
            @RequestParam @Positive Long pickupLocationId,
            @RequestParam @Positive Long dropoffLocationId) {
        return ApiResponse.of(service.get(tripId, pickupLocationId, dropoffLocationId));
    }
}
