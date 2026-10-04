package com.busgo.fleet;

import static com.busgo.fleet.MaintenanceDtos.*;
import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/operator")
public class MaintenanceController {
    private final MaintenanceService service;
    public MaintenanceController(MaintenanceService service) { this.service=service; }
    @GetMapping("/maintenance")
    public ApiResponse<PagedResponse<Maintenance>> list(@AuthenticationPrincipal CurrentUser actor,
            @RequestParam(required=false) @Positive Long busId, @RequestParam(required=false) Status status,
            @RequestParam(required=false) Type maintenanceType,@RequestParam(required=false) LocalDate fromDate,
            @RequestParam(required=false) LocalDate toDate,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ApiResponse.of(service.list(actor,busId,status,maintenanceType,fromDate,toDate,page,size));
    }
    @GetMapping("/buses/{busId}/maintenance")
    public ApiResponse<PagedResponse<Maintenance>> busList(@AuthenticationPrincipal CurrentUser actor,@PathVariable long busId,
            @RequestParam(required=false) Status status,@RequestParam(required=false) Type maintenanceType,
            @RequestParam(required=false) LocalDate fromDate,@RequestParam(required=false) LocalDate toDate,
            @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return ApiResponse.of(service.list(actor,busId,status,maintenanceType,fromDate,toDate,page,size));
    }
    @PostMapping("/buses/{busId}/maintenance") @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Maintenance> schedule(@AuthenticationPrincipal CurrentUser actor,@PathVariable long busId,@Valid @RequestBody Schedule input) { return ApiResponse.of(service.schedule(actor,busId,input)); }
    @PostMapping("/maintenance/{id}/start")
    public ApiResponse<Maintenance> start(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id) { return ApiResponse.of(service.start(actor,id)); }
    @PostMapping("/maintenance/{id}/complete")
    public ApiResponse<Maintenance> complete(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody Complete input) { return ApiResponse.of(service.complete(actor,id,input)); }
    @PostMapping("/maintenance/{id}/cancel")
    public ApiResponse<Maintenance> cancel(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody Cancel input) { return ApiResponse.of(service.cancel(actor,id,input)); }
    @GetMapping("/buses/{busId}/trips")
    public ApiResponse<PagedResponse<AssignedTrip>> trips(@AuthenticationPrincipal CurrentUser actor,@PathVariable long busId,
            @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return ApiResponse.of(service.assignedTrips(actor,busId,page,size)); }
    @GetMapping("/buses/{busId}/history")
    public ApiResponse<java.util.List<StatusHistory>> history(@AuthenticationPrincipal CurrentUser actor,@PathVariable long busId) { return ApiResponse.of(service.history(actor,busId)); }
    @GetMapping("/fleet/readiness")
    public ApiResponse<FleetWarnings> warnings(@AuthenticationPrincipal CurrentUser actor) { return ApiResponse.of(service.warnings(actor)); }
}
