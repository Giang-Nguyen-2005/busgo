package com.busgo.finance;

import static org.assertj.core.api.Assertions.*;
import static com.busgo.finance.CommissionMoney.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CommissionMoneyTest {
    BigDecimal d(String value) {return new BigDecimal(value);}
    @Test void explicitZeroAndNetBasis() {
        assertThat(accrue(d("400000"),d("0"))).isEqualByComparingTo("0");
        assertThat(accrue(d("400000"),d("5"))).isEqualByComparingTo("20000");
        assertThat(accrue(d("0.10"),d("5"))).isEqualByComparingTo("0.01");
    }
    @Test void partialRefundsReturnEveryCent() {
        var paid=d("0.30");var rate=d("5");var accrued=accrue(paid,rate);
        var first=reversal(paid,accrued,ZERO,ZERO,d("0.10"),rate);
        var second=reversal(paid,accrued,d("0.10"),first,d("0.10"),rate);
        var last=reversal(paid,accrued,d("0.20"),first.add(second),d("0.10"),rate);
        assertThat(first.add(second).add(last)).isEqualByComparingTo(accrued);
        assertThat(last).isEqualByComparingTo("0.01");
    }
    @Test void cannotOverReverse() {
        assertThatThrownBy(()->reversal(d("100"),d("5"),d("90"),d("4.50"),d("20"),d("5"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->accrue(d("100"),d("100.0001"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void channelsDoNotInferAssistedAdjustmentCustodyFromMethod() {
        assertThat(channel("WEB","MOCK_ONLINE","BOOKING",null)).isEqualTo("PLATFORM_MEDIATED_SIMULATED");
        for(String method:new String[]{"QR_TRANSFER","PAY_ON_BOARD"})
            assertThat(channel("PHONE",method,"BOOKING",null)).isEqualTo("OPERATOR_RECORDED_SIMULATED");
        assertThat(channel("PHONE","MOCK_ONLINE","BOOKING_MODIFICATION","OPERATOR_ADMIN")).isEqualTo("UNKNOWN_OR_UNVERIFIED");
        assertThat(channel("WEB","MOCK_ONLINE","BOOKING_MODIFICATION","CUSTOMER")).isEqualTo("PLATFORM_MEDIATED_SIMULATED");
    }
    @Test void vietnamDayIsHalfOpenUtcWindow() {
        var period=new FinanceDtos.Period(LocalDate.of(2026,10,21),LocalDate.of(2026,10,21));
        assertThat(period.start().toString()).isEqualTo("2026-10-20T17:00");
        assertThat(period.end().toString()).isEqualTo("2026-10-21T17:00");
        assertThatThrownBy(()->new FinanceDtos.Period(LocalDate.of(2026,1,1),LocalDate.of(2026,12,31))).isInstanceOf(RuntimeException.class);
    }
}
