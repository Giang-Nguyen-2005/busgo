package com.busgo.route.repository;

import com.busgo.route.entity.OperatorRouteFare;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRouteFareRepository extends JpaRepository<OperatorRouteFare, Long> {
    java.util.List<OperatorRouteFare> findByOperatorRouteIdOrderByFromRouteStopStopOrderAscToRouteStopStopOrderAsc(Long operatorRouteId);
}
