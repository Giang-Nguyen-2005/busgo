package com.busgo.trip.repository;

import com.busgo.trip.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripSeatSegmentInventoryRepository extends JpaRepository<TripSeatSegmentInventory, Long> {
    long countByTripSeatTripId(Long tripId);
    long countByTripSeatTripIdAndStatus(Long tripId, InventoryStatus status);
}
