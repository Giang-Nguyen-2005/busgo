package com.busgo.trip.operations;

import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.trip.operations.OperatorTripOperationsDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/operator/trips/{tripId}")
public class OperatorTripOperationsController {
    private final OperatorTripOperationsService service;

    public OperatorTripOperationsController(OperatorTripOperationsService service) {
        this.service = service;
    }

    @GetMapping("/passengers")
    public ApiResponse<PassengerManifest> passengers(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive Long tripId) {
        return ApiResponse.of(service.manifest(user, tripId));
    }

    @GetMapping("/occupancy")
    public ApiResponse<TripOccupancy> occupancy(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive Long tripId) {
        return ApiResponse.of(service.occupancy(user, tripId));
    }

    @PatchMapping("/status")
    public ApiResponse<TripStatusResponse> status(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive Long tripId,
            @Valid @RequestBody UpdateTripStatusRequest request) {
        return ApiResponse.of(service.updateStatus(user, tripId, request.status()));
    }
}
