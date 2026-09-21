package com.busgo.fleet.repository;

import com.busgo.fleet.entity.SeatTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatTemplateRepository extends JpaRepository<SeatTemplate, Long> {
    java.util.Optional<SeatTemplate> findByBusTypeIdAndSeatCode(Long busTypeId, String seatCode);
    java.util.List<SeatTemplate> findByBusTypeIdAndActiveTrueOrderByFloorAscRowAscColumnAsc(Long busTypeId);
}
