package com.fxorder.enums;

import java.math.BigDecimal;

public enum CurrencyCode {
    USD(1),
    JPY(100),
    CNY(1),
    EUR(1),
    KRW(1);

    private final BigDecimal unit;

    CurrencyCode(int unit) {
        this.unit = BigDecimal.valueOf(unit);
    }

    /**
     * 환율 고시 단위. JPY는 100엔당 환율로 고시되므로 100이다.
     * 환율에 수량을 곱한 뒤 이 값으로 나누어야 실제 환산 금액이 된다.
     */
    public BigDecimal getUnit() {
        return unit;
    }
}
