package com.busgo.booking.entity;

import com.busgo.common.entity.CreatedEntity;
import com.busgo.trip.entity.TripSeat;
import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "booking_items")
public class BookingItem extends CreatedEntity {
    @Column(name="gross_amount", precision=12, scale=2, nullable=false)
    private BigDecimal grossAmount;
    @Column(name="discount_amount", precision=12, scale=2, nullable=false)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(name="original_gross_amount", precision=12, scale=2, nullable=false, updatable=false)
    private BigDecimal originalGrossAmount;
    @Column(name="original_discount_amount", precision=12, scale=2, nullable=false, updatable=false)
    private BigDecimal originalDiscountAmount = BigDecimal.ZERO;
    @PrePersist
    private void initializeMoney() {
        if(grossAmount==null) grossAmount=unitPrice;
        if(originalGrossAmount==null) originalGrossAmount=grossAmount;
    }

    @Column(nullable = false)
    private boolean cancelled;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_seat_id", nullable = false)
    private TripSeat tripSeat;

    @Column(name = "seat_code", length = 20, nullable = false)
    private String seatCode;

    @Column(name = "passenger_name", length = 100)
    private String passengerName;

    @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;
}
