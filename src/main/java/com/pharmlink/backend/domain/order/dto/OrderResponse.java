package com.pharmlink.backend.domain.order.dto;

import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderStatus;

import java.time.Instant;

// 주문 목록 응답 항목 (8.1)
public record OrderResponse(
        Long orderId,
        String orderNumber,
        Long partnerId,
        String partnerName,
        OrderStatus status,
        long totalAmount,
        int itemCount,
        Long createdBy,
        Instant createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getOrderId(), order.getOrderNumber(), order.getPartner().getPartnerId(),
                order.getPartner().getName(), order.getStatus(), order.getTotalAmount(), order.getItems().size(),
                order.getCreatedBy(), order.getCreatedAt());
    }
}
