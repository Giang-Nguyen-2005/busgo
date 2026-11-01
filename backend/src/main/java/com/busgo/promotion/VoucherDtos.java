package com.busgo.promotion;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public final class VoucherDtos {
    private VoucherDtos() {}
    public record Terms(@NotBlank @Size(max=40) String code, @NotBlank @Size(max=120) String name,
        @Size(max=500) String description, @NotBlank @Pattern(regexp="PERCENT|FIXED_AMOUNT") String discountType,
        @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal discountValue,
        @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal maxDiscount,
        @NotNull @DecimalMin("0") @Digits(integer=10,fraction=2) BigDecimal minimumSpend,
        @NotNull OffsetDateTime startsAt, @NotNull OffsetDateTime endsAt,
        @Positive Integer totalLimit, @Positive Integer customerLimit, @Positive Long operatorRouteId) {}
    public record QuoteRequest(@NotBlank @Size(max=100) String holdToken, @NotBlank @Size(max=40) String voucherCode) {}
    public record Visibility(@NotNull Boolean customerVisible) {}
    public record OptionsRequest(@NotBlank @Size(max=100) String holdToken,
        @Min(0) @Max(100000) Integer page, @Min(1) @Max(20) Integer size) {}
    public enum OptionStatus { AVAILABLE, CONDITION_NOT_MET, UNAVAILABLE }
    public record Option(long id, String code, String name, String description, String discountType,
        BigDecimal discountValue, BigDecimal maxDiscount, BigDecimal minimumSpend,
        OffsetDateTime startsAt, OffsetDateTime endsAt, Integer customerLimit,
        OptionStatus status, String reasonCode, String message, Quote quote) {}
    public record Options(BigDecimal grossAmount, int seatCount, java.util.List<Option> options,
        com.busgo.common.response.PagedResponse.Pagination pagination) {}
    public record Quote(BigDecimal grossAmount, String voucherCode, String voucherName, String discountType,
        BigDecimal discountValue, BigDecimal discountAmount, BigDecimal totalAmount, boolean eligible, String reasonCode) {}
    public record Price(BigDecimal grossAmount, BigDecimal discountAmount, BigDecimal totalAmount,
        BigDecimal originalGrossAmount, BigDecimal originalDiscountAmount, String voucherCode, String voucherName) {}
}
