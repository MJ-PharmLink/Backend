package com.pharmlink.backend.domain.order.dto;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderStatus;

import java.time.Instant;

// 주문 승인 응답 (8.4). 승인되면 재고는 항상 차감되어 있으므로 inventory_deducted = true
public record OrderApproveResponse(
        Long orderId,
        OrderStatus status,
        boolean inventoryDeducted,
        Long deliveryId,
        DeliveryStatus deliveryStatus,
        Instant approvedAt
) {
    public static OrderApproveResponse from(Order order, Delivery delivery) {
        return new OrderApproveResponse(order.getOrderId(), order.getStatus(), true, delivery.getDeliveryId(),
                delivery.getStatus(), order.getApprovedAt());
    }
}
