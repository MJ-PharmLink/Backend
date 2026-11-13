package com.pharmlink.backend.domain.sales.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 마진율 계산 규칙 (DB 설계서 3.16): 마진 ÷ 매출 × 100, 소수 2자리 반올림. 매출이 0이면 0.00
// 납품 완료(9.4)의 건별 마진율과 마진 집계(11.3)의 합계 마진율이 같은 규칙을 쓴다
public final class MarginCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private MarginCalculator() {
    }

    public static BigDecimal marginRate(long salesAmount, long marginAmount) {
        if (salesAmount == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(marginAmount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(salesAmount), 2, RoundingMode.HALF_UP);
    }
}
