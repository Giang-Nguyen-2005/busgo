package com.busgo.trip.repository;

import com.busgo.trip.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripSeatSegmentInventoryRepository extends JpaRepository<TripSeatSegmentInventory, Long> {
    long countByTripSeatTripId(Long tripId);
    long countByTripSeatTripIdAndStatus(Long tripId, InventoryStatus status);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT COUNT(*)
            FROM trip_seats seat
            WHERE seat.trip_id = :tripId
              AND (SELECT COUNT(*) FROM trip_segments required_segment
                   WHERE required_segment.trip_id = :tripId
                     AND required_segment.segment_order >= :pickupOrder
                     AND required_segment.segment_order < :dropoffOrder) = :requiredSegmentCount
              AND NOT EXISTS (
                  SELECT 1
                  FROM trip_segments segment
                  LEFT JOIN trip_seat_segment_inventory inventory
                    ON inventory.trip_segment_id = segment.id
                   AND inventory.trip_seat_id = seat.id
                  WHERE segment.trip_id = :tripId
                    AND segment.segment_order >= :pickupOrder
                    AND segment.segment_order < :dropoffOrder
                    AND (inventory.id IS NULL OR inventory.status <> 'AVAILABLE')
              )
            """, nativeQuery = true)
    long countFullyAvailableSeats(
            @org.springframework.data.repository.query.Param("tripId") Long tripId,
            @org.springframework.data.repository.query.Param("pickupOrder") int pickupOrder,
            @org.springframework.data.repository.query.Param("dropoffOrder") int dropoffOrder,
            @org.springframework.data.repository.query.Param("requiredSegmentCount") int requiredSegmentCount);
}
