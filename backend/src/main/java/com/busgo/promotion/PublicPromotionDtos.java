package com.busgo.promotion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Public opt-in campaign facts only. Never includes personal eligibility or a net quote. */
public final class PublicPromotionDtos {
    private PublicPromotionDtos() {}
    public record Operator(long id, String name) {}
    public record Route(long id, String name, long pickupLocationId, long dropoffLocationId) {}
    public enum Eligibility { REQUIRES_CONTEXT, CONDITIONAL }
    public record Promotion(long id, Operator operator, Route route, String code, String name,
            String description, String discountType, BigDecimal discountValue, BigDecimal maxDiscount,
            BigDecimal minimumSpend, OffsetDateTime startsAt, OffsetDateTime endsAt,
            Integer customerLimit, Eligibility eligibility) {}
}
