package com.busgo.hold;

import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import com.busgo.hold.SeatHoldDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/seat-holds")
public class SeatHoldController {
    private final SeatHoldService service;

    public SeatHoldController(SeatHoldService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SeatHoldResponse> create(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateSeatHoldRequest request) {
        return ApiResponse.of(service.create(user, request));
    }

    @GetMapping("/{holdToken}")
    public ApiResponse<SeatHoldResponse> get(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @NotBlank @Size(max = 100) String holdToken) {
        return ApiResponse.of(service.get(user, holdToken));
    }

    @DeleteMapping("/{holdToken}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @NotBlank @Size(max = 100) String holdToken) {
        service.release(user, holdToken);
    }
}
