package com.busgo.payment;

import com.busgo.booking.entity.BookingStatus;
import com.busgo.payment.entity.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class PaymentTicketDtos {
    private PaymentTicketDtos() {}

    public record PaymentConfirmation(Long paymentId, Long bookingId, String bookingCode,
            PaymentMethod method, BigDecimal amount, PaymentStatus paymentStatus,
            BookingStatus bookingStatus, String transactionReference, OffsetDateTime paidAt) {}

    public record NamedSummary(Long id, String name) {}
    public record TicketStop(Long tripStopId, Long locationId, String name,
            OffsetDateTime time) {}
    public record TicketItem(Long ticketId, String ticketCode, String passengerName,
            String seatCode, String qrData) {}

    public record TicketBundle(Long bookingId, String bookingCode, BookingStatus status,
            PaymentStatus paymentStatus, PaymentMethod paymentMethod, BigDecimal amount,
            Long tripId, NamedSummary operator, NamedSummary route, TicketStop pickup,
            TicketStop dropoff, OffsetDateTime departureTime, OffsetDateTime arrivalTime,
            List<TicketItem> tickets) {}
}
