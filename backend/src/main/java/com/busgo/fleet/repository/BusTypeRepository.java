package com.busgo.fleet.repository;

import com.busgo.fleet.entity.BusType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusTypeRepository extends JpaRepository<BusType, Long> {
    java.util.Optional<BusType> findFirstByName(String name);
    java.util.List<BusType> findByStatusOrderByNameAsc(com.busgo.common.entity.ActiveStatus status);
    java.util.Optional<BusType> findByIdAndStatus(Long id, com.busgo.common.entity.ActiveStatus status);
}
