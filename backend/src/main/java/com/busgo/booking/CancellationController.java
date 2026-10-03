package com.busgo.booking;

import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
public class CancellationController {
    private final CancellationService service;
    public CancellationController(CancellationService service) { this.service=service; }

    @GetMapping("/api/v1/bookings/{id}/recovery")
    public ApiResponse<CancellationDtos.Recovery> customerRead(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id) {
        return ApiResponse.of(service.customerRead(user,id));
    }
    @PostMapping("/api/v1/bookings/{id}/cancel")
    public ApiResponse<CancellationDtos.Recovery> customerCancel(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id,@Valid @RequestBody CancellationDtos.Request request) {
        return ApiResponse.of(service.customerCancel(user,id,request));
    }
    @GetMapping("/api/v1/operator/bookings/{id}/recovery")
    public ApiResponse<CancellationDtos.Recovery> operatorRead(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id) {
        return ApiResponse.of(service.operatorRead(user,id));
    }
    @PostMapping("/api/v1/operator/bookings/{id}/cancel")
    public ApiResponse<CancellationDtos.Recovery> operatorCancel(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id,@Valid @RequestBody CancellationDtos.Request request) {
        return ApiResponse.of(service.operatorCancel(user,id,request));
    }
}
