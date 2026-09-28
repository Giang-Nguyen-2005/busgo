package com.busgo.admin;

import static com.busgo.admin.AdminOperatorDtos.*;
import static com.busgo.operator.OperatorStaffDtos.*;
import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.response.*;
import com.busgo.operator.entity.OperatorStatus;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/v1/admin/operators")
public class AdminOperatorController {
    private final AdminOperatorService service;
    public AdminOperatorController(AdminOperatorService service){this.service=service;}
    @GetMapping public PagedResponse<AdminOperatorListItem> list(
            @RequestParam(required=false) @Size(max=100) String q,@RequestParam(required=false) OperatorStatus status,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return service.list(q,status,page,size);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminOperatorDetail> create(@Valid @RequestBody CreateOperatorRequest r){return ApiResponse.of(service.create(r));}
    @GetMapping("/{id}") public ApiResponse<AdminOperatorDetail> detail(@PathVariable @Positive Long id){return ApiResponse.of(service.detail(id));}
    @PatchMapping("/{id}") public ApiResponse<AdminOperatorDetail> update(@PathVariable @Positive Long id,@Valid @RequestBody UpdateOperatorRequest r){return ApiResponse.of(service.update(id,r));}
    @PatchMapping("/{id}/status") public ApiResponse<AdminOperatorDetail> status(@PathVariable @Positive Long id,@Valid @RequestBody UpdateOperatorStatusRequest r){return ApiResponse.of(service.status(id,r));}
    @GetMapping("/{id}/staff") public PagedResponse<OperatorStaffResponse> staff(@PathVariable @Positive Long id,
            @RequestParam(required=false) @Size(max=100) String q,@RequestParam(required=false) ActiveStatus status,
            @RequestParam(required=false) OperatorRole role,@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return service.staff(id,q,status,role,page,size);}
}
