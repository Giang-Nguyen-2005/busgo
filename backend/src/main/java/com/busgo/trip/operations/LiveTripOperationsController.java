package com.busgo.trip.operations;

import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated
@RequestMapping("/api/v1/operator/trips/{tripId}")
public class LiveTripOperationsController {
    private final LiveTripOperationsService service;
    public LiveTripOperationsController(LiveTripOperationsService service) { this.service=service; }
    @GetMapping("/operations")
    public ApiResponse<LiveTripState> current(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId) { return ApiResponse.of(service.current(user,tripId)); }
    @GetMapping("/operational-history")
    public ApiResponse<List<LiveTripOperationsService.History>> history(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId) { return ApiResponse.of(service.history(user,tripId)); }
    @PostMapping("/delay")
    public ApiResponse<LiveTripState> delay(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId,@Valid @RequestBody LiveTripOperationsService.Update input) { return ApiResponse.of(service.update(user,tripId,input,false)); }
    @PostMapping("/eta")
    public ApiResponse<LiveTripState> eta(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId,@Valid @RequestBody LiveTripOperationsService.Update input) { return ApiResponse.of(service.update(user,tripId,input,true)); }
}
