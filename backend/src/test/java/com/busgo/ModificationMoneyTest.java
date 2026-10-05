package com.busgo;
import com.busgo.booking.ModificationMoney;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ModificationMoneyTest {
    @Test void paidPositiveUsesCollectedBalance() {
        var q=ModificationMoney.quote(new BigDecimal("250000.00"),new BigDecimal("320000.25"),new BigDecimal("250000.00"),true);
        assertThat(q.fareDelta()).isEqualByComparingTo("70000.25"); assertThat(q.collectionRequired()).isEqualByComparingTo("70000.25"); assertThat(q.newAmountDue()).isZero();
    }
    @Test void paidNegativeRefundsDifference() {
        var q=ModificationMoney.quote(new BigDecimal("320000"),new BigDecimal("250000"),new BigDecimal("320000"),true);
        assertThat(q.refundRequired()).isEqualByComparingTo("70000"); assertThat(q.collectionRequired()).isZero();
    }
    @Test void unpaidPositiveOwesWholeNewFare() {
        var q=ModificationMoney.quote(new BigDecimal("250000"),new BigDecimal("320000"),BigDecimal.ZERO,false);
        assertThat(q.collectionRequired()).isZero(); assertThat(q.newAmountDue()).isEqualByComparingTo("320000"); assertThat(q.refundRequired()).isZero();
    }
    @Test void unpaidNegativeNeverCreatesRefund() {
        var q=ModificationMoney.quote(new BigDecimal("320000"),new BigDecimal("250000"),BigDecimal.ZERO,false);
        assertThat(q.refundRequired()).isZero(); assertThat(q.newAmountDue()).isEqualByComparingTo("250000");
    }
    @Test void sameFareCreatesNoCashMovement() {
        var q=ModificationMoney.quote(new BigDecimal("250000"),new BigDecimal("250000"),new BigDecimal("250000"),true);
        assertThat(q.collectionRequired()).isZero(); assertThat(q.refundRequired()).isZero(); assertThat(q.fareDelta()).isZero();
    }
}
