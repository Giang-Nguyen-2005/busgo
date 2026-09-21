package com.busgo.operator.repository;

import com.busgo.operator.entity.TransportOperator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportOperatorRepository extends JpaRepository<TransportOperator, Long> {
    java.util.Optional<TransportOperator> findByCode(String code);
}
