package com.busgo.booking;

import java.math.BigDecimal;
import static com.busgo.booking.ModificationDtos.*;

/** Fare changes and cash execution are deliberately separate. */
public final class ModificationMoney {
    private ModificationMoney() {}
    public static Money quote(BigDecimal oldTotal, BigDecimal newTotal, BigDecimal collected, boolean paid) {
        BigDecimal zero = new BigDecimal("0.00");
        return new Money(oldTotal, newTotal, newTotal.subtract(oldTotal), collected,
                paid ? newTotal.subtract(collected).max(zero) : zero,
                paid ? collected.subtract(newTotal).max(zero) : zero,
                paid ? zero : newTotal);
    }
}
