package com.busgo.trip.entity;

import com.busgo.common.entity.ActiveStatus;
import com.busgo.common.entity.BaseEntity;
import com.busgo.location.entity.Location;
import com.busgo.route.entity.RouteStop;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "trip_stops")
public class TripStop extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_route_stop_id")
    private RouteStop sourceRouteStop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "planned_arrival_time")
    private LocalDateTime plannedArrivalTime;

    @Column(name = "planned_departure_time")
    private LocalDateTime plannedDepartureTime;

    @Column(name = "allow_pickup", nullable = false)
    private boolean allowPickup;

    @Column(name = "allow_dropoff", nullable = false)
    private boolean allowDropoff;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private ActiveStatus status;
}
