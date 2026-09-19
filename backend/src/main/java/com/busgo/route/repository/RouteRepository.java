package com.busgo.route.repository;

import com.busgo.route.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {
    org.springframework.data.domain.Page<Route> findByStatus(
            com.busgo.route.entity.RouteStatus status, org.springframework.data.domain.Pageable pageable);
    java.util.Optional<Route> findByIdAndStatus(Long id, com.busgo.route.entity.RouteStatus status);
}
