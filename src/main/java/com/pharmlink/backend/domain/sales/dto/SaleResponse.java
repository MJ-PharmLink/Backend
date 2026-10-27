package com.pharmlink.backend.domain.sales.dto;

import com.pharmlink.backend.domain.sales.entity.Sale;

import java.math.BigDecimal;
import java.time.LocalDate;

// 매출 목록 응답 항목 (11.1). 금액·마진율은 납품 완료 시 저장한 값(주문 라인 스냅샷 기준)
public record SaleResponse(
        Long saleId,
        Long orderId,
        Long deliveryId,
        Long partnerId,
        String partnerName,
        long salesAmount,
        long costAmount,
        long marginAmount,
        BigDecimal marginRate,
        LocalDate saleDate
) {
    public static SaleResponse from(Sale sale) {
        return new SaleResponse(sale.getSaleId(), sale.getOrder().getOrderId(), sale.getDelivery().getDeliveryId(),
                sale.getPartner().getPartnerId(), sale.getPartner().getName(), sale.getSalesAmount(),
                sale.getCostAmount(), sale.getMarginAmount(), sale.getMarginRate(), sale.getSaleDate());
    }
}
