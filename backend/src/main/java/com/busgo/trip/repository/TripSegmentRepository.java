package com.busgo.trip.repository;

import com.busgo.trip.entity.TripSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripSegmentRepository extends JpaRepository<TripSegment, Long> {
    List<TripSegment> findByTripIdOrderBySegmentOrderAsc(Long tripId);
    long countByTripId(Long tripId);
}
