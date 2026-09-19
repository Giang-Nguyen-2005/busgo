package com.busgo.fleet.entity;

import com.busgo.common.entity.AuditedEntity;
import com.busgo.common.entity.ActiveStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "bus_types")
public class BusType extends AuditedEntity {
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "seat_count", nullable = false)
    private Integer seatCount;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private ActiveStatus status;
}
