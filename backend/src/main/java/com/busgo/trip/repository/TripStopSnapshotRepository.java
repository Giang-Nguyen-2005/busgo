package com.busgo.trip.repository;

import com.busgo.trip.entity.TripStop;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripStopSnapshotRepository extends JpaRepository<TripStop, Long> {
    List<TripStop> findByTripIdOrderByStopOrderAsc(Long tripId);
    Optional<TripStop> findByTripIdAndLocationId(Long tripId, Long locationId);
    long countByTripId(Long tripId);
}
