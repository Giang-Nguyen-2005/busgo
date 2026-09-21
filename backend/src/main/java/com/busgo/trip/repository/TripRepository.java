package com.busgo.trip.repository;

import com.busgo.trip.entity.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long> {
    Optional<Trip> findByOperatorRouteIdAndBusIdAndDepartureTime(
            Long operatorRouteId, Long busId, LocalDateTime departureTime);

    @Query(value = """
            select t from Trip t
            join fetch t.operatorRoute opr
            join fetch opr.route r
            join fetch t.bus b
            join fetch b.busType
            where opr.operator.id = :operatorId
              and (:startTime is null or t.departureTime >= :startTime)
              and (:endTime is null or t.departureTime < :endTime)
              and (:routeId is null or r.id = :routeId)
              and (:busId is null or b.id = :busId)
              and (:status is null or t.status = :status)
            """, countQuery = """
            select count(t) from Trip t
            where t.operatorRoute.operator.id = :operatorId
              and (:startTime is null or t.departureTime >= :startTime)
              and (:endTime is null or t.departureTime < :endTime)
              and (:routeId is null or t.operatorRoute.route.id = :routeId)
              and (:busId is null or t.bus.id = :busId)
              and (:status is null or t.status = :status)
            """)
    Page<Trip> searchOwned(@Param("operatorId") Long operatorId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("routeId") Long routeId,
            @Param("busId") Long busId,
            @Param("status") TripStatus status,
            Pageable pageable);

    @Query("""
            select t from Trip t
            join fetch t.operatorRoute opr
            join fetch opr.route
            join fetch t.bus b
            join fetch b.busType
            where t.id = :id and opr.operator.id = :operatorId
            """)
    Optional<Trip> findOwnedById(@Param("id") Long id, @Param("operatorId") Long operatorId);

    @Query("""
            select t from Trip t
            join fetch t.operatorRoute opr
            join fetch opr.operator
            join fetch opr.route
            join fetch t.bus b
            join fetch b.busType
            where t.id = :id
            """)
    Optional<Trip> findPublicById(@Param("id") Long id);

    @Query("""
            select (count(t) > 0) from Trip t
            where t.bus.id = :busId
              and t.status <> com.busgo.trip.entity.TripStatus.CANCELLED
              and t.departureTime < :newArrival
              and t.estimatedArrivalTime > :newDeparture
            """)
    boolean hasScheduleConflict(@Param("busId") Long busId,
            @Param("newDeparture") LocalDateTime newDeparture,
            @Param("newArrival") LocalDateTime newArrival);
}
