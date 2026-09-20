package com.busgo.booking;

import com.busgo.booking.entity.BookingStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

public final class BookingDtos {
    private BookingDtos() {}

    public record CreateBookingRequest(
            @NotBlank @Size(max = 100) String holdToken,
            @NotBlank @Size(max = 100) String contactName,
            @NotBlank @Size(max = 20) String contactPhone,
            @NotBlank @Email @Size(max = 150) String contactEmail) {
        public CreateBookingRequest {
            holdToken = strip(holdToken);
            contactName = strip(contactName);
            contactPhone = strip(contactPhone);
            contactEmail = contactEmail == null ? null
                    : contactEmail.strip().toLowerCase(Locale.ROOT);
        }
    }

    public record OperatorSummary(Long id, String name) {}
    public record RouteSummary(Long id, String name) {}
    public record StopSummary(Long tripStopId, Long locationId, String name,
            OffsetDateTime time) {}
    public record Contact(String name, String phone, String email) {}
    public record BookingSeat(Long tripSeatId, String seatCode, String passengerName,
            BigDecimal unitPrice) {}

    public record BookingResponse(Long bookingId, String bookingCode, BookingStatus status,
            Long tripId, OperatorSummary operator, RouteSummary route, StopSummary pickup,
            StopSummary dropoff, Contact contact, List<BookingSeat> seats,
            BigDecimal pricePerSeat, BigDecimal totalAmount, OffsetDateTime createdAt) {}

    public record BookingListItem(Long bookingId, String bookingCode, BookingStatus status,
            Long tripId, String routeName, String operatorName, StopSummary pickup,
            StopSummary dropoff, OffsetDateTime departureTime, List<String> seats,
            BigDecimal totalAmount, OffsetDateTime createdAt) {}

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
