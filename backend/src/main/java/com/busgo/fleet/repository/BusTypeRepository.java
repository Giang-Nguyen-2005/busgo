package com.busgo.fleet.repository;

import com.busgo.fleet.entity.BusType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusTypeRepository extends JpaRepository<BusType, Long> {
}
