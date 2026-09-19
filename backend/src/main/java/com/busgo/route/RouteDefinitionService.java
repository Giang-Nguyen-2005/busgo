package com.busgo.route;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.exception.BusinessException;
import com.busgo.common.exception.ResourceNotFoundException;
import com.busgo.common.response.PagedResponse;
import com.busgo.route.RouteDtos.*;
import com.busgo.route.entity.*;
import com.busgo.route.repository.*;
import java.util.HashSet;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RouteDefinitionService {
    private final RouteRepository routes;
    private final RouteStopRepository stops;

    public RouteDefinitionService(RouteRepository routes, RouteStopRepository stops) {
        this.routes = routes;
        this.stops = stops;
    }

    @Transactional(readOnly = true)
    public PagedResponse<RouteDefinitionResponse> catalog(int page, int size) {
        var result = routes.findByStatus(RouteStatus.ACTIVE,
                PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))));
        return PagedResponse.from(result.map(this::response));
    }

    @Transactional(readOnly = true)
    public RouteDefinitionResponse getActive(Long id) {
        return response(requireUsable(id));
    }

    public Route requireUsable(Long id) {
        Route route = routes.findByIdAndStatus(id, RouteStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ROUTE_NOT_FOUND", "Route was not found."));
        List<RouteStop> layout = activeStops(route.getId());
        if (layout.size() < 2) throw invalidRoute();
        var locations = new HashSet<Long>();
        int previousOffset = -1;
        for (int index = 0; index < layout.size(); index++) {
            RouteStop stop = layout.get(index);
            if (stop.getStopOrder() == null || stop.getStopOrder() != index + 1
                    || stop.getEstimatedOffsetMinutes() == null
                    || stop.getEstimatedOffsetMinutes() < 0
                    || (index > 0 && stop.getEstimatedOffsetMinutes() <= previousOffset)
                    || !locations.add(stop.getLocation().getId())) {
                throw invalidRoute();
            }
            previousOffset = stop.getEstimatedOffsetMinutes();
        }
        if (!route.getOriginLocation().getId().equals(layout.get(0).getLocation().getId())
                || !route.getDestinationLocation().getId().equals(layout.get(layout.size() - 1).getLocation().getId())) {
            throw invalidRoute();
        }
        return route;
    }

    public RouteDefinitionResponse response(Route route) {
        return new RouteDefinitionResponse(route.getId(), route.getName(), location(route.getOriginLocation()),
                location(route.getDestinationLocation()), route.getEstimatedDistanceKm(),
                route.getEstimatedDurationMinutes(), route.getStatus(),
                activeStops(route.getId()).stream().map(this::stop).toList());
    }

    private List<RouteStop> activeStops(Long routeId) {
        return stops.findByRouteIdAndStatusOrderByStopOrderAsc(routeId, ActiveStatus.ACTIVE);
    }

    private RouteStopResponse stop(RouteStop stop) {
        return new RouteStopResponse(stop.getId(), stop.getStopOrder(), location(stop.getLocation()),
                stop.isAllowPickup(), stop.isAllowDropoff(), stop.getEstimatedOffsetMinutes(), stop.getStatus());
    }

    private RouteLocationResponse location(com.busgo.location.entity.Location location) {
        return new RouteLocationResponse(location.getId(), location.getName(), location.getProvince(), location.getDistrict());
    }

    private static BusinessException invalidRoute() {
        return new BusinessException("INVALID_ROUTE", "Route has an invalid active stop layout.",
                HttpStatus.CONFLICT, null);
    }
}
