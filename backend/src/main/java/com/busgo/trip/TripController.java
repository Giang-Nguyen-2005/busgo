package com.busgo.trip;

import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.trip.TripDtos.*;
import com.busgo.trip.entity.TripStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/operator/trips")
public class TripController {
    private final TripService service;

    public TripController(TripService service) { this.service = service; }

    @GetMapping
    public PagedResponse<TripSummaryResponse> list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) @Positive Long routeId,
            @RequestParam(required = false) @Positive Long busId,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(user, date, routeId, busId, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TripSummaryResponse> create(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateTripRequest request) {
        return ApiResponse.of(service.create(user, request));
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> get(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long tripId) {
        return ApiResponse.of(service.get(user, tripId));
    }
}
