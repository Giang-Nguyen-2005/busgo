package com.busgo.trip.repository;

import com.busgo.trip.entity.TripSeat;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripSeatRepository extends JpaRepository<TripSeat, Long> {
    List<TripSeat> findByTripIdOrderByFloorAscRowAscColumnAsc(Long tripId);
    long countByTripId(Long tripId);
}
