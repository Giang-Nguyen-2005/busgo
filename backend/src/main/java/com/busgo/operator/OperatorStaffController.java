package com.busgo.operator;

import static com.busgo.operator.OperatorStaffDtos.*;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/v1/operator/staff")
public class OperatorStaffController {
    private final OperatorStaffService service;
    public OperatorStaffController(OperatorStaffService service) { this.service=service; }
    @GetMapping
    public PagedResponse<OperatorStaffResponse> list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required=false) @Size(max=100) String q,
            @RequestParam(required=false) ActiveStatus status,
            @RequestParam(required=false) OperatorRole role,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(user,q,status,role,page,size);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OperatorStaffResponse> create(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateOperatorStaffRequest request) { return ApiResponse.of(service.create(user,request)); }
    @PatchMapping("/{staffId}")
    public ApiResponse<OperatorStaffResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Positive Long staffId, @Valid @RequestBody UpdateOperatorStaffRequest request) {
        return ApiResponse.of(service.update(user,staffId,request));
    }
}
