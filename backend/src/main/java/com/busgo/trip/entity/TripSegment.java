package com.busgo.trip.entity;

import com.busgo.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "trip_segments")
public class TripSegment extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_trip_stop_id", nullable = false)
    private TripStop fromTripStop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_trip_stop_id", nullable = false)
    private TripStop toTripStop;

    @Column(name = "segment_order", nullable = false)
    private Integer segmentOrder;
}
