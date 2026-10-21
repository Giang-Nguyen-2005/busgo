package com.busgo.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CommissionMoney {
    private CommissionMoney() {}
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    public static BigDecimal accrue(BigDecimal amount, BigDecimal rate) {
        if (amount.signum()<0 || rate.signum()<0 || rate.compareTo(new BigDecimal("100"))>0)
            throw new IllegalArgumentException("Invalid commission basis/rate");
        return amount.multiply(rate).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
    }
    /** Cumulative rounding assigns residual cents to the last refund, without over-reversal. */
    public static BigDecimal reversal(BigDecimal paid, BigDecimal accrued, BigDecimal refundedBefore,
                                      BigDecimal reversedBefore, BigDecimal refund, BigDecimal rate) {
        var cumulative=refundedBefore.add(refund);
        if (refund.signum()<0 || cumulative.compareTo(paid)>0 || reversedBefore.compareTo(accrued)>0)
            throw new IllegalArgumentException("Refund exceeds original payment");
        var target=cumulative.compareTo(paid)==0 ? accrued : accrue(cumulative,rate).min(accrued);
        var result=target.subtract(reversedBefore);
        if(result.signum()<0) throw new IllegalArgumentException("Inconsistent prior reversal");
        return result;
    }
    public static String channel(String source,String method,String purpose,String actor) {
        if ("BOOKING_MODIFICATION".equals(purpose))
            return "WEB".equals(source) && "CUSTOMER".equals(actor)
                ? "PLATFORM_MEDIATED_SIMULATED" : "UNKNOWN_OR_UNVERIFIED";
        if ("BOOKING".equals(purpose) && "WEB".equals(source) && "MOCK_ONLINE".equals(method))
            return "PLATFORM_MEDIATED_SIMULATED";
        if ("BOOKING".equals(purpose) && "PHONE".equals(source)
                && ("PAY_ON_BOARD".equals(method) || "QR_TRANSFER".equals(method)))
            return "OPERATOR_RECORDED_SIMULATED";
        return "UNKNOWN_OR_UNVERIFIED";
    }
}
