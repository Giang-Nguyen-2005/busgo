package com.busgo.promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Money in cents. Stable input order determines the residual-cent allocation. */
public final class PromotionMoney {
    private PromotionMoney() {}
    public static final BigDecimal CENT = new BigDecimal("0.01");
    public static BigDecimal discount(String type, BigDecimal value, BigDecimal cap, BigDecimal gross) {
        BigDecimal amount = "PERCENT".equals(type)
                ? gross.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP) : value;
        if (cap != null) amount = amount.min(cap);
        return amount.min(gross).setScale(2, RoundingMode.HALF_UP);
    }
    public static List<BigDecimal> allocate(BigDecimal discount, List<BigDecimal> gross) {
        BigDecimal total = gross.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (gross.isEmpty() || discount.signum() < 0 || discount.compareTo(total) > 0)
            throw new IllegalArgumentException("Invalid discount allocation");
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal remaining = discount.setScale(2, RoundingMode.UNNECESSARY);
        for (BigDecimal value : gross) {
            BigDecimal part = total.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : discount.multiply(value).divide(total, 2, RoundingMode.DOWN);
            result.add(part); remaining = remaining.subtract(part);
        }
        for (int i = 0; remaining.signum() > 0; i = (i + 1) % gross.size()) {
            if (result.get(i).add(CENT).compareTo(gross.get(i)) <= 0) {
                result.set(i, result.get(i).add(CENT)); remaining = remaining.subtract(CENT);
            }
        }
        return List.copyOf(result);
    }
}
