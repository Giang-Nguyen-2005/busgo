package com.busgo;

import static org.assertj.core.api.Assertions.*;
import com.busgo.promotion.PromotionMoney;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromotionMoneyTest {
    @Test void percentageRoundsHalfUpAndCaps() {
        assertThat(PromotionMoney.discount("PERCENT",new BigDecimal("12.50"),null,new BigDecimal("100.04"))).isEqualByComparingTo("12.51");
        assertThat(PromotionMoney.discount("PERCENT",new BigDecimal("50"),new BigDecimal("20"),new BigDecimal("100"))).isEqualByComparingTo("20");
    }
    @Test void fixedCannotExceedGross() { assertThat(PromotionMoney.discount("FIXED_AMOUNT",new BigDecimal("500"),null,new BigDecimal("100"))).isEqualByComparingTo("100"); }
    @Test void residualGoesToStableFirstIdentity() {
        assertThat(PromotionMoney.allocate(new BigDecimal("10.00"),List.of(new BigDecimal("100"),new BigDecimal("100"),new BigDecimal("100"))))
            .containsExactly(new BigDecimal("3.34"),new BigDecimal("3.33"),new BigDecimal("3.33"));
    }
    @Test void unequalGrossProratesAndConservesCents() {
        var parts=PromotionMoney.allocate(new BigDecimal("33.33"),List.of(new BigDecimal("10"),new BigDecimal("90")));
        assertThat(parts).containsExactly(new BigDecimal("3.34"),new BigDecimal("29.99"));
    }
    @Test void rejectsNegativeDiscount() { assertThatThrownBy(()->PromotionMoney.allocate(new BigDecimal("-1"),List.of(BigDecimal.TEN))).isInstanceOf(IllegalArgumentException.class); }
}
