package com.busgo.route.entity;

import com.busgo.common.entity.BaseEntity;
import com.busgo.common.entity.ActiveStatus;

import com.busgo.location.entity.Location;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "route_stops")
public class RouteStop extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Column(name = "allow_pickup", nullable = false)
    private boolean allowPickup;

    @Column(name = "allow_dropoff", nullable = false)
    private boolean allowDropoff;

    @Column(name = "estimated_offset_minutes", nullable = false)
    private Integer estimatedOffsetMinutes;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private ActiveStatus status;
}
