package com.busgo.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class ModificationDtos {
    private ModificationDtos() {}
    public enum Type { SEAT_CHANGE, TRIP_CHANGE }
    public record Selection(@NotNull @Positive Long bookingItemId, @NotNull @Positive Long targetSeatId) {}
    public record Request(@NotNull Type type, @NotNull @Positive Long targetTripId,
                          @NotEmpty @Size(max=20) List<@Valid Selection> items) {}
    public record Rule(boolean allowed, String reasonCode, String message) {}
    public record Item(long bookingItemId, long oldSeatId, String oldSeatLabel, long newSeatId,
                       String newSeatLabel, BigDecimal oldFare, BigDecimal newFare) {}
    public record CurrentItem(long bookingItemId, long seatId, String seatLabel) {}
    public record Eligibility(Rule seatChange, Rule tripChange, String bookingCode, String source,
                              String contactName, long tripId, List<CurrentItem> items) {}
    public record Money(BigDecimal currentTotal, BigDecimal newTotal, BigDecimal fareDelta,
                        BigDecimal alreadyCollected, BigDecimal collectionRequired,
                        BigDecimal refundRequired, BigDecimal newAmountDue) {}
    public record Quote(Type type, long sourceTripId, long targetTripId, String bookingCode,
                        String journeyName, OffsetDateTime sourceDeparture, OffsetDateTime targetDeparture,
                        Money money, List<Item> items) {}
    public record History(long id, String code, String status, Quote quote, String actorType,
                          String actorName, OffsetDateTime createdAt, OffsetDateTime expiresAt,
                          OffsetDateTime completedAt) {}
}
