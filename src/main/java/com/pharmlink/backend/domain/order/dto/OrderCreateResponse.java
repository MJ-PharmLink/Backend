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
    // createdAt은 DB에 저장된 값 (자동 기록값의 초 미만은 DB 저장 시 반올림되므로)
    public static OrderCreateResponse from(Order order, Instant createdAt) {
        return new OrderCreateResponse(order.getOrderId(), order.getOrderNumber(), order.getPartner().getPartnerId(),
                order.getPartner().getName(), order.getStatus(),
                order.getItems().stream().map(item -> OrderLineResponse.from(item, false)).toList(),
                order.getTotalAmount(), order.getCreatedBy(), createdAt);
    }
}
