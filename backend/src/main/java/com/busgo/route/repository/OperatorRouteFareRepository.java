package com.busgo.route.repository;

import com.busgo.route.entity.OperatorRouteFare;
import com.busgo.common.entity.ActiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRouteFareRepository extends JpaRepository<OperatorRouteFare, Long> {
    java.util.Optional<OperatorRouteFare> findFirstByOperatorRouteIdAndFromRouteStopIdAndToRouteStopId(
            Long operatorRouteId, Long fromRouteStopId, Long toRouteStopId);

    java.util.List<OperatorRouteFare> findByOperatorRouteIdOrderByFromRouteStopStopOrderAscToRouteStopStopOrderAsc(Long operatorRouteId);

    @org.springframework.data.jpa.repository.Query("""
            select f from OperatorRouteFare f
            where f.operatorRoute.id = :operatorRouteId
              and f.fromRouteStop.id = :fromStopId
              and f.toRouteStop.id = :toStopId
              and f.status = :status
            order by f.id
            limit 1
            """)
    java.util.Optional<OperatorRouteFare> findExactActiveFare(
            @org.springframework.data.repository.query.Param("operatorRouteId") Long operatorRouteId,
            @org.springframework.data.repository.query.Param("fromStopId") Long fromStopId,
            @org.springframework.data.repository.query.Param("toStopId") Long toStopId,
            @org.springframework.data.repository.query.Param("status") ActiveStatus status);
}
