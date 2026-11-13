package com.pharmlink.backend.domain.order.dto;

import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderStatus;

import java.time.Instant;

// 주문 취소 응답 (8.5)
public record OrderCancelResponse(
        Long orderId,
        String orderNumber,
        OrderStatus status,
        String cancelReason,
        Long cancelledBy,
        Instant cancelledAt
) {
    public static OrderCancelResponse from(Order order) {
        return new OrderCancelResponse(order.getOrderId(), order.getOrderNumber(), order.getStatus(),
                order.getCancelReason(), order.getCancelledBy(), order.getCancelledAt());
    }
}
