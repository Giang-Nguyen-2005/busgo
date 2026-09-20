package com.busgo.booking.entity;

import com.busgo.common.entity.AuditedEntity;
import com.busgo.trip.entity.Trip;
import com.busgo.trip.entity.TripStop;
import com.busgo.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "bookings")
public class Booking extends AuditedEntity {
    @Column(name = "booking_code", length = 50, nullable = false, unique = true)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pickup_trip_stop_id", nullable = false)
    private TripStop pickupTripStop;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dropoff_trip_stop_id", nullable = false)
    private TripStop dropoffTripStop;

    @Column(name = "contact_name", length = 100, nullable = false)
    private String contactName;

    @Column(name = "contact_phone", length = 20, nullable = false)
    private String contactPhone;

    @Column(name = "contact_email", length = 150, nullable = false)
    private String contactEmail;

    @Column(name = "total_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private BookingStatus status;

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<BookingItem> items = new ArrayList<>();
}
