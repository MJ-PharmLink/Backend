package com.pharmlink.backend.domain.dashboard.dto;

import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.order.entity.OrderStatus;

import java.time.Instant;

// 최근 주문/납품 현황 (12.3). 납품이 아직 없으면(PENDING·CANCELLED) delivery_id·delivery_status는 null
public record RecentOrderResponse(
        Long orderId,
        String orderNumber,
        Long partnerId,
        String partnerName,
        OrderStatus orderStatus,
        Long deliveryId,
        DeliveryStatus deliveryStatus,
        long totalAmount,
        Instant createdAt
) {
}
