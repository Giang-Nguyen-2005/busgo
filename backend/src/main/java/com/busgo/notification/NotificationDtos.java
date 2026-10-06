package com.busgo.notification;

import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotNull;

public final class NotificationDtos {
    private NotificationDtos() {}
    public record Notification(long id, NotificationType type, String title, String message,
            String bookingCode, String navigationTarget, OffsetDateTime createdAt, OffsetDateTime readAt) {}
    public record Preferences(@NotNull Boolean bookingPaymentEmail, @NotNull Boolean bookingChangeEmail,
            @NotNull Boolean tripReminderEmail) {
        public boolean allows(NotificationType type) {
            return switch(type.category) {
                case BOOKING_PAYMENT -> bookingPaymentEmail;
                case BOOKING_CHANGE -> bookingChangeEmail;
                case TRIP_REMINDER -> tripReminderEmail;
            };
        }
    }
    public record Delivery(long id, String status, int attemptCount, OffsetDateTime lastAttemptAt,
            OffsetDateTime nextAttemptAt, OffsetDateTime sentAt, String errorSummary) {}
}
