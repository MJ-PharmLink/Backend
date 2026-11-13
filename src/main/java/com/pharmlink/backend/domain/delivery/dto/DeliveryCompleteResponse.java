package com.pharmlink.backend.domain.delivery.dto;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.sales.entity.Sale;

import java.time.Instant;

// 납품 완료 응답 (9.4). 함께 생성된 매출의 금액을 담는다
public record DeliveryCompleteResponse(
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        Long saleId,
        long salesAmount,
        long costAmount,
        long marginAmount,
        Instant deliveredAt
) {
    public static DeliveryCompleteResponse from(Delivery delivery, Sale sale) {
        return new DeliveryCompleteResponse(delivery.getDeliveryId(), delivery.getOrder().getOrderId(),
                delivery.getStatus(), sale.getSaleId(), sale.getSalesAmount(), sale.getCostAmount(),
                sale.getMarginAmount(), delivery.getDeliveredAt());
    }
}
