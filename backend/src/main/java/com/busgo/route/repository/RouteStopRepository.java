package com.busgo.route.repository;

import com.busgo.route.entity.RouteStop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {
    java.util.List<RouteStop> findByRouteIdOrderByStopOrderAsc(Long routeId);
    java.util.List<RouteStop> findByRouteIdAndStatusOrderByStopOrderAsc(
            Long routeId, com.busgo.common.entity.ActiveStatus status);
    java.util.Optional<RouteStop> findByIdAndRouteId(Long id, Long routeId);
}
