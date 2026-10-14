package com.pharmlink.backend.domain.order.dto;

import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderStatus;

import java.time.Instant;
import java.util.List;

// 주문 등록 응답 (8.2). 라인에 unit_cost 없음
public record OrderCreateResponse(
        Long orderId,
        String orderNumber,
        Long partnerId,
        String partnerName,
        OrderStatus status,
        List<OrderLineResponse> items,
        long totalAmount,
        Long createdBy,
        Instant createdAt
) {
    public static OrderCreateResponse from(Order order) {
        return new OrderCreateResponse(order.getOrderId(), order.getOrderNumber(), order.getPartner().getPartnerId(),
                order.getPartner().getName(), order.getStatus(),
                order.getItems().stream().map(item -> OrderLineResponse.from(item, false)).toList(),
                order.getTotalAmount(), order.getCreatedBy(), order.getCreatedAt());
    }
}
