package com.busgo.booking;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class PartialCancellationDtos {
    private PartialCancellationDtos() {}
    public record Request(@NotEmpty @Size(max=20) List<@NotNull @Positive Long> bookingItemIds) {}
    public record Rule(boolean allowed,String reasonCode,String message) {}
    public record Item(long bookingItemId,String passengerName,String seatCode,BigDecimal amount,
                       boolean cancelled,Rule eligibility) {}
    public record Eligibility(Rule eligibility,String bookingCode,String journeyName,OffsetDateTime departure,
                              List<Item> items) {}
    public record Quote(String bookingCode,String journeyName,OffsetDateTime departure,List<Item> items,
                        BigDecimal currentTotal,BigDecimal cancelledAmount,BigDecimal newTotal,
                        BigDecimal netCollected,BigDecimal refundRequired,BigDecimal newAmountDue) {}
    public record History(long id,String code,String actorType,String actorName,OffsetDateTime completedAt,Quote quote) {}
}
