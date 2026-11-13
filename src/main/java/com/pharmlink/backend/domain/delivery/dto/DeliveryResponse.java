package com.pharmlink.backend.domain.delivery.dto;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.order.entity.Order;

import java.time.Instant;

// 납품 목록 응답 항목 (9.1). 거래처·주문번호·총액은 주문에서 가져온다
public record DeliveryResponse(
        Long deliveryId,
        Long orderId,
        String orderNumber,
        Long partnerId,
        String partnerName,
        DeliveryStatus status,
        long totalAmount,
        Instant createdAt
) {
    public static DeliveryResponse from(Delivery delivery) {
        Order order = delivery.getOrder();
        return new DeliveryResponse(delivery.getDeliveryId(), order.getOrderId(), order.getOrderNumber(),
                order.getPartner().getPartnerId(), order.getPartner().getName(), delivery.getStatus(),
                order.getTotalAmount(), delivery.getCreatedAt());
    }
}
