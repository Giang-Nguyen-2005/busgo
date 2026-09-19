package com.busgo.route.repository;

import com.busgo.route.entity.OperatorRoute;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRouteRepository extends JpaRepository<OperatorRoute, Long> {
    @org.springframework.data.jpa.repository.Query(value = """
            select o from OperatorRoute o join fetch o.route
            where o.operator.id = :operatorId
            """, countQuery = "select count(o) from OperatorRoute o where o.operator.id = :operatorId")
    org.springframework.data.domain.Page<OperatorRoute> findByOperatorId(
            @org.springframework.data.repository.query.Param("operatorId") Long operatorId,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select o from OperatorRoute o join fetch o.route
            where o.id = :id and o.operator.id = :operatorId
            """)
    java.util.Optional<OperatorRoute> findOwnedById(
            @org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("operatorId") Long operatorId);

    java.util.Optional<OperatorRoute> findByOperatorIdAndRouteId(Long operatorId, Long routeId);
}
