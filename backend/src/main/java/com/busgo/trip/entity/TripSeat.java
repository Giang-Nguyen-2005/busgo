package com.busgo.trip.entity;

import com.busgo.common.entity.BaseEntity;
import com.busgo.fleet.entity.SeatTemplate;
import com.busgo.fleet.entity.SeatType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "trip_seats")
public class TripSeat extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_seat_template_id")
    private SeatTemplate sourceSeatTemplate;

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
}
