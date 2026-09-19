package com.busgo.route;

import com.busgo.common.response.*;
import com.busgo.common.security.CurrentUser;
import com.busgo.operator.OperatorContextService;
import com.busgo.route.RouteDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/operator")
public class OperatorRouteController {
    private final RouteDefinitionService definitions;
    private final OperatorRouteService routes;
    private final OperatorRouteFareService fares;
    private final OperatorContextService context;

    public OperatorRouteController(RouteDefinitionService definitions, OperatorRouteService routes,
            OperatorRouteFareService fares, OperatorContextService context) {
        this.definitions = definitions;
        this.routes = routes;
        this.fares = fares;
        this.context = context;
    }

    @GetMapping("/route-catalog")
    public PagedResponse<RouteDefinitionResponse> catalog(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        context.requireAdminOperator(user);
        return definitions.catalog(page, size);
    }

    @GetMapping("/route-catalog/{routeId}")
    public ApiResponse<RouteDefinitionResponse> catalogDetail(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long routeId) {
        context.requireAdminOperator(user);
        return ApiResponse.of(definitions.getActive(routeId));
    }

    @GetMapping("/routes")
    public PagedResponse<OperatorRouteResponse> list(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return routes.list(user, page, size);
    }

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OperatorRouteResponse> attach(@AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody AttachRouteRequest request) {
        return ApiResponse.of(routes.attach(user, request));
    }

    @GetMapping("/routes/{operatorRouteId}")
    public ApiResponse<OperatorRouteResponse> get(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long operatorRouteId) {
        return ApiResponse.of(routes.get(user, operatorRouteId));
    }

    @PatchMapping("/routes/{operatorRouteId}")
    public ApiResponse<OperatorRouteResponse> update(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long operatorRouteId, @Valid @RequestBody UpdateOperatorRouteRequest request) {
        return ApiResponse.of(routes.update(user, operatorRouteId, request));
    }

    @GetMapping("/routes/{operatorRouteId}/fares")
    public ApiResponse<List<FareResponse>> fares(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long operatorRouteId) {
        return ApiResponse.of(fares.list(user, operatorRouteId));
    }

    @PutMapping("/routes/{operatorRouteId}/fares")
    public ApiResponse<List<FareResponse>> replaceFares(@AuthenticationPrincipal CurrentUser user,
            @PathVariable Long operatorRouteId, @Valid @RequestBody ReplaceFaresRequest request) {
        return ApiResponse.of(fares.replace(user, operatorRouteId, request));
    }
}
