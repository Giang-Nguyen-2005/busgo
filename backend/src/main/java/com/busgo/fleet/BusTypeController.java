package com.busgo.fleet;

import com.busgo.common.response.ApiResponse;
import com.busgo.fleet.FleetDtos.BusTypeResponse;
import com.busgo.operator.OperatorContextService;
import com.busgo.common.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/operator/bus-types")
public class BusTypeController {
    private final BusTypeService service;
    private final OperatorContextService context;

    public BusTypeController(BusTypeService service, OperatorContextService context) {
        this.service = service;
        this.context = context;
    }

    @GetMapping
    public ApiResponse<List<BusTypeResponse>> list(@AuthenticationPrincipal CurrentUser user) {
        context.requireAdminOperator(user);
        return ApiResponse.of(service.listActive());
    }

    @GetMapping("/{id}")
    public ApiResponse<BusTypeResponse> get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        context.requireAdminOperator(user);
        return ApiResponse.of(service.getActive(id));
    }
}
