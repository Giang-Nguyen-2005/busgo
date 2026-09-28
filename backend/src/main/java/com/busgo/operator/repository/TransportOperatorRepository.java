package com.busgo.operator.repository;

import com.busgo.operator.entity.TransportOperator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TransportOperatorRepository extends JpaRepository<TransportOperator, Long> {
    java.util.Optional<TransportOperator> findByCode(String code);
    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TransportOperator o where o.id = :id")
    java.util.Optional<TransportOperator> lockById(@Param("id") Long id);
}
