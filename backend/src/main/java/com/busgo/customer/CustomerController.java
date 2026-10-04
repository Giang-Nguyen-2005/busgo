package com.busgo.customer;

import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.customer.CustomerDtos.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operator/customers")
public class CustomerController {
    private final CustomerService service;
    public CustomerController(CustomerService service) { this.service=service; }
    @GetMapping
    public ApiResponse<PagedResponse<Summary>> directory(@AuthenticationPrincipal CurrentUser user,@ModelAttribute CustomerFilter filter) {
        return ApiResponse.of(service.directory(user,filter));
    }
    @GetMapping("/{customerKey}")
    public ApiResponse<Detail> detail(@AuthenticationPrincipal CurrentUser user,@PathVariable String customerKey,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size) {
        return ApiResponse.of(service.detail(user,customerKey,page,size));
    }
}
