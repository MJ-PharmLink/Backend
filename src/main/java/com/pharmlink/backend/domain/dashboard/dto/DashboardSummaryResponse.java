package com.pharmlink.backend.domain.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// 대시보드 요약 (12.1). 금액은 기간 기준, 건수는 조회 시점의 현재 상태값
public record DashboardSummaryResponse(
        Period period,
        long totalSales,
        long totalPurchases,
        long totalMargin,
        BigDecimal marginRate,
        long pendingOrderCount,
        long waitingDeliveryCount,
        long shippedDeliveryCount,
        long lowStockCount,
        long expiringSoonCount,
        long expiredCount
) {
    public record Period(LocalDate startDate, LocalDate endDate) {
    }
}
