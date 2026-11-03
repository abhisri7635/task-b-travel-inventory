package com.example.navan.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {

    private Money() {}

    /** Scale = 2 (2-decimal currencies such as EUR, USD). */
    public static long toLong(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                     .movePointRight(2)
                     .longValueExact();
    }

    public static BigDecimal fromLong(long minor) {
        return BigDecimal.valueOf(minor, 2);
    }
}
