package com.busgo.finance;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import jakarta.validation.constraints.*;
import com.busgo.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public final class FinanceDtos {
    private FinanceDtos() {}
    public record Period(LocalDate from,LocalDate to) {
        public Period {
            if(from==null || to==null || to.isBefore(from) || to.isAfter(from.plusDays(92)))
                throw new BusinessException("FINANCE_PERIOD_INVALID","Chọn khoảng thời gian tối đa 93 ngày.",HttpStatus.BAD_REQUEST,null);
        }
        public LocalDateTime start() {return from.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();}
        public LocalDateTime end() {return to.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();}
    }
    public record PolicyInput(@Positive long operatorId,@NotNull @DecimalMin("0") @DecimalMax("100")
        @Digits(integer=3,fraction=4) BigDecimal rate,@NotNull OffsetDateTime validFrom,OffsetDateTime validTo,
        @NotBlank @Size(max=500) String reason) {}
    public record Policy(long id,long operatorId,BigDecimal rate,OffsetDateTime validFrom,OffsetDateTime validTo,
        String state,long createdBy,String reason,OffsetDateTime activatedAt) {}
    public record Event(long id,String sourceKey,String eventType,long operatorId,long bookingId,long paymentId,Long refundId,
        String purpose,String origin,String channel,BigDecimal amount,Long policyId,BigDecimal rate,
        BigDecimal commission,BigDecimal operatorPayable,BigDecimal platformReceivable,BigDecimal grossContext,
        BigDecimal discountContext,OffsetDateTime occurredAt,OffsetDateTime createdAt,String currency,String verification) {}
    public record Totals(BigDecimal collections,BigDecimal refunds,BigDecimal netCollections,BigDecimal commissionAccrual,
        BigDecimal commissionReversal,BigDecimal netCommission,BigDecimal operatorPayable,BigDecimal platformReceivable,
        BigDecimal unknownAmount,BigDecimal unpricedAmount,BigDecimal grossContext,BigDecimal discountContext,
        long eventCount,long sourceCount,long missingCount,BigDecimal sourceCollections,BigDecimal sourceRefunds,
        long unknownCount,long unpricedCount) {}
    public record Overview(Period period,String timezone,String currency,String verification,Long operatorId,
        String operatorName,String reconciliationStatus,Totals totals) {}
    public record Page<T>(List<T> data,int page,int size,long total) {}
    public record RunInput(@Positive long operatorId,@NotNull LocalDate from,@NotNull LocalDate to,boolean projectMissing) {}
    public record Run(long id,long operatorId,LocalDate from,LocalDate to,String status,int sourceCount,int projectedCount,
        int exceptionCount,OffsetDateTime startedAt,OffsetDateTime completedAt,Overview snapshot) {}
    public record ExceptionItem(long id,String sourceKey,String reason) {}
    public record Statement(long id,long operatorId,long runId,OffsetDateTime issuedAt,Overview snapshot) {}
    public record Operator(long id,String name) {}
    public record OperatorComparison(long id,String name,Totals totals) {}
}
