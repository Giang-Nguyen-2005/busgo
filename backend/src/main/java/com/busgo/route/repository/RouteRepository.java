package com.busgo.route.repository;

import com.busgo.route.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select r from Route r where r.name=:name or
            (r.originLocation.id=:origin and r.destinationLocation.id=:destination)
            """)
    java.util.List<Route> findCatalogueCandidates(
            @org.springframework.data.repository.query.Param("name") String name,
            @org.springframework.data.repository.query.Param("origin") Long origin,
            @org.springframework.data.repository.query.Param("destination") Long destination);
    java.util.Optional<Route> findFirstByNameAndOriginLocationIdAndDestinationLocationId(
            String name, Long originLocationId, Long destinationLocationId);

    org.springframework.data.domain.Page<Route> findByStatus(
            com.busgo.route.entity.RouteStatus status, org.springframework.data.domain.Pageable pageable);
    java.util.Optional<Route> findByIdAndStatus(Long id, com.busgo.route.entity.RouteStatus status);
}
