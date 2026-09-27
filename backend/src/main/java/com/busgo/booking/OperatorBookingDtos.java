package com.busgo.booking;

import com.busgo.booking.entity.BookingStatus;
import com.busgo.payment.entity.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class OperatorBookingDtos {
    private OperatorBookingDtos() {}

    public record Contact(String name, String phone, String email) {}
    public record Stop(Long tripStopId, Long locationId, String name, OffsetDateTime time) {}
    public record RouteSummary(Long id, String name) {}
    public record TripSummary(Long id, com.busgo.trip.entity.TripStatus status,
            OffsetDateTime departureTime, OffsetDateTime estimatedArrivalTime) {}
    public record CustomerSummary(Long id, String fullName, String email, String phone) {}

    public record OperatorBookingListItem(Long bookingId, String bookingCode,
            BookingStatus status, PaymentStatus paymentStatus, Long tripId,
            RouteSummary route, Contact contact, Stop pickup, Stop dropoff,
            int seatCount, BigDecimal totalAmount, OffsetDateTime createdAt) {}

    public record TicketSummary(Long id, String ticketCode, String passengerName,
            String seatCode, Long paymentId, OffsetDateTime createdAt) {}
    public record BookingItemSummary(Long bookingItemId, Long tripSeatId, String seatCode,
            String passengerName, BigDecimal unitPrice, TicketSummary ticket) {}
    public record PaymentSummary(Long id, PaymentMethod method, BigDecimal amount,
            PaymentStatus status, String transactionReference, OffsetDateTime paidAt,
            OffsetDateTime createdAt) {}

    public record OperatorBookingDetail(Long bookingId, String bookingCode,
            BookingStatus status, TripSummary trip, RouteSummary route,
            CustomerSummary customer, Contact contact, Stop pickup, Stop dropoff,
            List<BookingItemSummary> items, List<PaymentSummary> payments,
            BigDecimal totalAmount, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
