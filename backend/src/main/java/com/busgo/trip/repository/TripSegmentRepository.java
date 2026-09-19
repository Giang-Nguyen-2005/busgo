package com.busgo.trip.repository;

import com.busgo.trip.entity.TripSegment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripSegmentRepository extends JpaRepository<TripSegment, Long> {
    List<TripSegment> findByTripIdOrderBySegmentOrderAsc(Long tripId);

    @Query("""
            select segment from TripSegment segment
            join fetch segment.fromTripStop
            join fetch segment.toTripStop
            where segment.trip.id = :tripId
              and segment.segmentOrder >= :pickupOrder
              and segment.segmentOrder < :dropoffOrder
            order by segment.segmentOrder
            """)
    List<TripSegment> findJourneySegments(@Param("tripId") Long tripId,
            @Param("pickupOrder") int pickupOrder, @Param("dropoffOrder") int dropoffOrder);

    long countByTripId(Long tripId);
}
