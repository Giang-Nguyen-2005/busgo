package com.busgo.booking;

import com.busgo.booking.entity.BookingStatus;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class CancellationDtos {
    private CancellationDtos() {}
    public record Request(@Size(max=500) String note) {}
    public record Refund(Long id, Long paymentId, BigDecimal amount, OffsetDateTime refundedAt,
                         Long refundedBy, String reasonCode, String note) {}
    public record Ticket(Long id, String ticketCode, String status, OffsetDateTime voidedAt) {}
    public record History(String fromStatus, String toStatus, Long actorId, String reasonCode,
                          String note, OffsetDateTime changedAt) {}
    public record Recovery(Long bookingId, BookingStatus status, boolean eligible, String ineligibleReason,
                           boolean paid, OffsetDateTime paymentDueAt, OffsetDateTime customerCutoffAt,
                           OffsetDateTime cancelledAt, Long cancelledBy, String reasonCode, String note,
                           List<Refund> refunds, List<Ticket> tickets, List<History> history) {}
}
