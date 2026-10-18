package com.pharmlink.backend.domain.sales.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MarginCalculatorTest {

    @Test
    @DisplayName("마진율 = 마진 ÷ 매출 × 100, 명세 9.4 예시 125,000 / 37,500 → 30.00")
    void marginRateOfSpecExample() {
        assertThat(MarginCalculator.marginRate(125_000, 37_500)).isEqualByComparingTo("30.00");
    }

    @Test
    @DisplayName("소수 셋째 자리에서 반올림: 2/9 → 22.22, 5/9 → 55.56")
    void marginRateRoundsHalfUpToTwoDecimals() {
        assertThat(MarginCalculator.marginRate(900, 200)).isEqualTo(new BigDecimal("22.22"));
        assertThat(MarginCalculator.marginRate(900, 500)).isEqualTo(new BigDecimal("55.56"));
    }

    @Test
    @DisplayName("매출이 0이면 0으로 나누지 않고 0.00")
    void marginRateIsZeroWhenSalesIsZero() {
        assertThat(MarginCalculator.marginRate(0, 0)).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("원가가 매출보다 크면 음수 마진율")
    void negativeMarginRate() {
        assertThat(MarginCalculator.marginRate(1_000, -250)).isEqualTo(new BigDecimal("-25.00"));
    }
}
