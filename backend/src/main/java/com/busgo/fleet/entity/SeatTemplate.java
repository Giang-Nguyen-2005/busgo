package com.busgo.fleet.entity;

import com.busgo.common.entity.BaseEntity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "seat_templates")
public class SeatTemplate extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bus_type_id", nullable = false)
    private BusType busType;

    @Column(name = "seat_code", length = 20, nullable = false)
    private String seatCode;

    @Column(name = "row_no", nullable = false)
    private Integer row;

    @Column(name = "column_no", nullable = false)
    private Integer column;

    @Column(name = "floor_no", nullable = false)
    private Integer floor;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "seat_type", length = 30, nullable = false)
    private SeatType seatType;

    @Column(name = "active", nullable = false)
    private boolean active;
}
