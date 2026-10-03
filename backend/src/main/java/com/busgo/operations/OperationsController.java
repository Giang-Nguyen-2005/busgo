package com.busgo.operations;

import com.busgo.common.response.ApiResponse;
import com.busgo.common.security.CurrentUser;
import static com.busgo.operations.OperationsDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/operator")
public class OperationsController {
    private final OperationsService service;
    public OperationsController(OperationsService service) { this.service=service; }
    @GetMapping("/employees")
    public ApiResponse<?> employees(@AuthenticationPrincipal CurrentUser user) { return ApiResponse.of(service.employees(user)); }
    @GetMapping("/employees/{id}")
    public ApiResponse<?> employee(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id) { return ApiResponse.of(service.employee(user,id)); }
    @PostMapping("/employees")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<?> create(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody EmployeeInput input) { return ApiResponse.of(service.saveEmployee(user,null,input)); }
    @PatchMapping("/employees/{id}")
    public ApiResponse<?> edit(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id,@Valid @RequestBody EmployeeInput input) { return ApiResponse.of(service.saveEmployee(user,id,input)); }
    @GetMapping("/trips/{tripId}/crew")
    public ApiResponse<?> crew(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId) { return ApiResponse.of(service.crew(user,tripId)); }
    @PutMapping("/trips/{tripId}/crew")
    public ApiResponse<?> crewWrite(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId,@Valid @RequestBody CrewReplacement input) { return ApiResponse.of(service.replaceCrew(user,tripId,input)); }
    @GetMapping("/trips/{tripId}/attendance")
    public ApiResponse<?> attendance(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId) { return ApiResponse.of(service.attendance(user,tripId)); }
    @PostMapping("/trips/{tripId}/tickets/{ticketId}/{command:check-in|board|direct-board|no-show}")
    public ApiResponse<?> attendanceWrite(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId,@PathVariable @Positive long ticketId,@PathVariable String command,@Valid @RequestBody PickupContext input) { return ApiResponse.of(service.transition(user,tripId,ticketId,command,input)); }
    @PostMapping("/trips/{tripId}/booking-items/{itemId}/no-show")
    public ApiResponse<?> reservationAbsent(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId,@PathVariable @Positive long itemId,@Valid @RequestBody PickupContext input) { return ApiResponse.of(service.reservationNoShow(user,tripId,itemId,input)); }
    @GetMapping("/trips/{tripId}/pickups")
    public ApiResponse<?> pickups(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId) { return ApiResponse.of(service.pickups(user,tripId)); }
    @PostMapping("/trips/{tripId}/stops/{stopId}/close-pickup")
    public ApiResponse<?> close(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId,@PathVariable @Positive long stopId,@Valid @RequestBody PickupContext input) {
        if(input.stopId()!=stopId) throw new com.busgo.common.exception.BusinessException("WRONG_PICKUP_STOP","Pickup context must match path.",org.springframework.http.HttpStatus.CONFLICT,null);
        return ApiResponse.of(service.closePickup(user,tripId,stopId,input.reason()));
    }
    @GetMapping("/trips/{tripId}/history")
    public ApiResponse<?> history(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long tripId) { return ApiResponse.of(service.histories(user,tripId)); }
}
