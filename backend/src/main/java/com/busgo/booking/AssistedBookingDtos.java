package com.busgo.booking;

import com.busgo.payment.entity.PaymentMethod;
import com.busgo.booking.entity.BookingStatus;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class AssistedBookingDtos {
    private AssistedBookingDtos() {}
    public record CreateRequest(@NotNull @Positive Long tripId,
            @NotNull @Positive Long pickupLocationId, @NotNull @Positive Long dropoffLocationId,
            @NotEmpty @Size(max = 5) List<@NotNull @Positive Long> tripSeatIds,
            @NotBlank @Size(max = 100) String contactName,
            @NotBlank @Size(max = 20) String contactPhone,
            @Email @Size(max = 150) String contactEmail,
            @NotNull PaymentMethod paymentMethod) {
        public CreateRequest {
            contactName = contactName == null ? null : contactName.strip();
            contactPhone = contactPhone == null ? null : contactPhone.strip();
            contactEmail = contactEmail == null || contactEmail.isBlank() ? null : contactEmail.strip();
        }
    }
    public record RecordPayment(@NotNull PaymentMethod method, @Size(max = 500) String referenceNote) {}
    public record PaymentLink(String path) {}
    // Deliberately excludes database IDs, contact, account, actor and internal payment data.
    public record PublicStop(String name, java.time.OffsetDateTime time) {}
    public record PublicPayment(String bookingCode, String operator, String journey,
            PublicStop pickup, PublicStop dropoff,
            List<String> seats, BigDecimal amount, PaymentMethod method, BookingStatus status,
            boolean mockPayment) {}
}
