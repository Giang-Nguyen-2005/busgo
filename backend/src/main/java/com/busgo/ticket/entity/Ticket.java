package com.busgo.ticket.entity;

import com.busgo.booking.entity.Booking;
import com.busgo.booking.entity.BookingItem;
import com.busgo.common.entity.CreatedEntity;
import com.busgo.payment.entity.Payment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tickets")
public class Ticket extends CreatedEntity {
    @Column(nullable=false)
    private boolean replaced;
    @Column(nullable = false, length = 10)
    private String status = "VALID";
    @Column(name = "voided_at")
    private java.time.LocalDateTime voidedAt;
    @Column(name = "voided_by_user_id")
    private Long voidedByUserId;
    @Column(name = "void_reason", length = 30)
    private String voidReason;
    @Column(name = "ticket_code", length = 50, nullable = false, unique = true)
    private String ticketCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_item_id", nullable = false)
    private BookingItem bookingItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "passenger_name", length = 100, nullable = false)
    private String passengerName;

    @Column(name = "seat_code", length = 20, nullable = false)
    private String seatCode;
}
