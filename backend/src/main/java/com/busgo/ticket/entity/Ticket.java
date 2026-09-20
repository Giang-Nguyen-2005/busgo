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
    @Column(name = "ticket_code", length = 50, nullable = false, unique = true)
    private String ticketCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_item_id", nullable = false, unique = true)
    private BookingItem bookingItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "passenger_name", length = 100, nullable = false)
    private String passengerName;

    @Column(name = "seat_code", length = 20, nullable = false)
    private String seatCode;
}
