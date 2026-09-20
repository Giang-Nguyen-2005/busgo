package com.busgo.trip.entity;

import com.busgo.booking.entity.BookingItem;
import com.busgo.common.entity.BaseEntity;
import com.busgo.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "trip_seat_segment_inventory")
public class TripSeatSegmentInventory extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_seat_id", nullable = false)
    private TripSeat tripSeat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_segment_id", nullable = false)
    private TripSegment tripSegment;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(name = "status", length = 30, nullable = false)
    private InventoryStatus status;

    @Column(name = "hold_token", length = 100)
    private String holdToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "held_by_user_id")
    private User heldByUser;

    @Column(name = "hold_expires_at")
    private LocalDateTime holdExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_item_id")
    private BookingItem bookingItem;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
