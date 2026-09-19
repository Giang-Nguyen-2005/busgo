package com.busgo.trip.repository;

import com.busgo.trip.entity.TripStop;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripStopSnapshotRepository extends JpaRepository<TripStop, Long> {
    List<TripStop> findByTripIdOrderByStopOrderAsc(Long tripId);
    long countByTripId(Long tripId);
}
