package com.pharmlink.backend.domain.delivery.dto;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderItem;

import java.time.Instant;
import java.util.List;

// 납품 상세 응답 (9.2). 품목은 주문 라인을 그대로 쓰고 원가(unit_cost)는 내보내지 않는다
public record DeliveryDetailResponse(
        Long deliveryId,
        Long orderId,
        String orderNumber,
        Long partnerId,
        String partnerName,
        DeliveryStatus status,
        List<Line> items,
        long totalAmount,
        Instant createdAt,
        Instant shippedAt,
        Instant deliveredAt
) {
    public record Line(Long itemId, String itemCode, String itemName, int quantity, long unitPrice, long lineAmount) {
        static Line from(OrderItem orderItem) {
            return new Line(orderItem.getItem().getItemId(), orderItem.getItem().getItemCode(),
                    orderItem.getItem().getItemName(), orderItem.getQuantity(), orderItem.getUnitPrice(),
                    orderItem.getLineAmount());
        }
    }

    public static DeliveryDetailResponse from(Delivery delivery) {
        Order order = delivery.getOrder();
        return new DeliveryDetailResponse(delivery.getDeliveryId(), order.getOrderId(), order.getOrderNumber(),
                order.getPartner().getPartnerId(), order.getPartner().getName(), delivery.getStatus(),
                order.getItems().stream().map(Line::from).toList(), order.getTotalAmount(), delivery.getCreatedAt(),
                delivery.getShippedAt(), delivery.getDeliveredAt());
    }
}
