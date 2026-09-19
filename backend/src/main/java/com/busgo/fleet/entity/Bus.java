package com.busgo.fleet.entity;

import com.busgo.common.entity.AuditedEntity;

import java.time.LocalDateTime;
import com.busgo.operator.entity.TransportOperator;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "buses")
public class Bus extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operator_id", nullable = false)
    private TransportOperator operator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_type_id", nullable = false)
    private BusType busType;

    @Column(name = "license_plate", length = 30, nullable = false)
    private String licensePlate;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private BusStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
