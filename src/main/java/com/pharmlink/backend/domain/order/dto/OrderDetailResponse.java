package com.pharmlink.backend.domain.order.dto;

import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderStatus;

import java.time.Instant;
import java.util.List;

// 주문 상세 응답 (8.3). delivery_id는 납품 미생성(PENDING·CANCELLED)이면 null
public record OrderDetailResponse(
        Long orderId,
        String orderNumber,
        Long partnerId,
        String partnerName,
        OrderStatus status,
        List<OrderLineResponse> items,
        long totalAmount,
        Long deliveryId,
        String cancelReason,
        Instant approvedAt,
        Instant cancelledAt,
        Long cancelledBy,
        Long createdBy,
        Instant createdAt
) {
    public static OrderDetailResponse from(Order order, Long deliveryId) {
        return new OrderDetailResponse(order.getOrderId(), order.getOrderNumber(), order.getPartner().getPartnerId(),
                order.getPartner().getName(), order.getStatus(),
                order.getItems().stream().map(item -> OrderLineResponse.from(item, true)).toList(),
                order.getTotalAmount(), deliveryId, order.getCancelReason(), order.getApprovedAt(),
                order.getCancelledAt(), order.getCancelledBy(), order.getCreatedBy(), order.getCreatedAt());
    }
}
