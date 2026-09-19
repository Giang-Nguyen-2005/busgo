package com.busgo.route;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.route.entity.RouteStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class RouteDtos {
    private RouteDtos() {}

    public record RouteLocationResponse(Long id, String name, String province, String district) {}
    public record RouteStopResponse(Long id, Integer stopOrder, RouteLocationResponse location,
            boolean allowPickup, boolean allowDropoff, Integer estimatedOffsetMinutes, ActiveStatus status) {}
    public record RouteDefinitionResponse(Long id, String name, RouteLocationResponse origin,
            RouteLocationResponse destination, BigDecimal estimatedDistanceKm,
            Integer estimatedDurationMinutes, RouteStatus status, List<RouteStopResponse> stops) {}
    public record OperatorRouteResponse(Long id, ActiveStatus status, RouteDefinitionResponse route) {}

    public record AttachRouteRequest(@NotNull @Positive Long routeId) {}
    public record UpdateOperatorRouteRequest(@NotNull ActiveStatus status) {}

    public record FareInput(@NotNull @Positive Long fromRouteStopId,
            @NotNull @Positive Long toRouteStopId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal price) {}
    public record ReplaceFaresRequest(@NotNull List<@Valid FareInput> fares) {}
    public record FareResponse(Long id, Long fromRouteStopId, Long toRouteStopId,
            BigDecimal price, ActiveStatus status) {}
}
