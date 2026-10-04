package com.busgo.customer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import com.busgo.common.response.PagedResponse;

public final class CustomerDtos {
    private CustomerDtos() {}
    public enum Type { ACCOUNT, OFFLINE_CONTACT }
    public enum Sort { LATEST, NAME, BOOKINGS, MOCK_PAID }
    public record Attendance(long boarded, long noShow, long checkedIn, long unrecorded) {}
    public record Money(BigDecimal grossMockPaid, BigDecimal mockRefunds, BigDecimal netMockPaid) {}
    public record Summary(String customerKey, Type customerType, String displayName, String phone,
            String email, OffsetDateTime latestBookingAt, String latestJourney, long totalBookings,
            long confirmedBookings, long cancelledBookings, long webBookings, long phoneBookings,
            long boardedJourneys, Attendance attendance, Money money) {}
    public record BookingHistory(long bookingId, String bookingCode, String source, String contactName,
            String contactPhone, String contactEmail, String routeName, String pickup, String dropoff,
            String seats, String bookingStatus, String paymentMethod, String paymentStatus,
            long validTickets, long voidTickets, Attendance attendance, Money money,
            String cancellationReason, OffsetDateTime cancelledAt, OffsetDateTime createdAt,
            List<PaymentEvent> payments, List<RefundEvent> refunds) {}
    public record PaymentEvent(long id, String method, String status, BigDecimal amount,
            OffsetDateTime paidAt, OffsetDateTime createdAt) {}
    public record RefundEvent(long id, BigDecimal amount, String reason, OffsetDateTime refundedAt) {}
    public record Detail(Summary summary, PagedResponse<BookingHistory> bookings) {}
}
