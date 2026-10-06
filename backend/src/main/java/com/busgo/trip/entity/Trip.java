package com.busgo.trip.entity;

import com.busgo.common.entity.AuditedEntity;
import com.busgo.fleet.entity.Bus;
import com.busgo.route.entity.OperatorRoute;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "trips")
public class Trip extends AuditedEntity {
    @Column(name = "delay_minutes", nullable = false)
    private int delayMinutes;
    @Column(name = "delay_reason", length = 500)
    private String delayReason;
    @Column(name = "expected_departure_at")
    private LocalDateTime expectedDepartureAt;
    @Column(name = "expected_arrival_at")
    private LocalDateTime expectedArrivalAt;
    @Column(name = "actual_departure_at")
    private LocalDateTime actualDepartureAt;
    @Column(name = "actual_arrival_at")
    private LocalDateTime actualArrivalAt;
    @Column(name = "operational_updated_at")
    private LocalDateTime operationalUpdatedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_route_id", nullable = false)
    private OperatorRoute operatorRoute;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_id", nullable = false)
    private Bus bus;

    @Column(name = "departure_time", nullable = false)
    private LocalDateTime departureTime;

    @Column(name = "estimated_arrival_time", nullable = false)
    private LocalDateTime estimatedArrivalTime;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private TripStatus status;
}
