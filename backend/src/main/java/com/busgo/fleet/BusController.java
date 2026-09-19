package com.busgo.fleet;

import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.fleet.FleetDtos.*;
import com.busgo.fleet.entity.BusStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/operator/buses")
public class BusController {
    private final BusService service;

    public BusController(BusService service) { this.service = service; }

    @GetMapping
    public PagedResponse<BusResponse> list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required = false) BusStatus status,
            @RequestParam(required = false) @Positive Long busTypeId,
            @RequestParam(defaultValue = "") @Size(max = 30) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(user, status, busTypeId, q, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusResponse> create(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateBusRequest request) {
        return ApiResponse.of(service.create(user, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BusResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return ApiResponse.of(service.get(user, id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<BusResponse> update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
            @Valid @RequestBody UpdateBusRequest request) {
        return ApiResponse.of(service.update(user, id, request));
    }
}
