package com.pharmlink.backend.domain.delivery.dto;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;

import java.time.Instant;

// 출고 완료 응답 (9.3)
public record DeliveryShipResponse(
        Long deliveryId,
        Long orderId,
        DeliveryStatus status,
        Instant shippedAt
) {
    public static DeliveryShipResponse from(Delivery delivery) {
        return new DeliveryShipResponse(delivery.getDeliveryId(), delivery.getOrder().getOrderId(),
                delivery.getStatus(), delivery.getShippedAt());
    }
}
