package com.busgo.promotion;

import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController @Validated @RequestMapping("/api/v1/operator/vouchers")
public class VoucherController {
    private final VoucherService service;
    public VoucherController(VoucherService service) { this.service=service; }
    @GetMapping public PagedResponse<Map<String,Object>> list(@AuthenticationPrincipal CurrentUser user,
        @RequestParam(required=false) @Size(max=120) String q,@RequestParam(required=false) String state,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.list(user,q,state,page,size); }
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ApiResponse<Map<String,Object>> create(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody VoucherDtos.Terms terms) { return ApiResponse.of(service.create(user,terms)); }
    @GetMapping("/{id}") public ApiResponse<Map<String,Object>> detail(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id) { return ApiResponse.of(service.detail(user,id)); }
    @PatchMapping("/{id}") public ApiResponse<Map<String,Object>> edit(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id,@Valid @RequestBody VoucherDtos.Terms terms) { return ApiResponse.of(service.edit(user,id,terms)); }
    @PostMapping("/{id}/activate") public ApiResponse<Map<String,Object>> activate(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id) { return ApiResponse.of(service.state(user,id,true)); }
    @PostMapping("/{id}/pause") public ApiResponse<Map<String,Object>> pause(@AuthenticationPrincipal CurrentUser user,@PathVariable @Positive long id) { return ApiResponse.of(service.state(user,id,false)); }
}
