package com.pharmlink.backend.domain.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pharmlink.backend.domain.order.entity.OrderItem;

// 주문 라인 응답. unit_cost(원가 스냅샷)는 상세(8.3)에만 포함하고 등록 응답(8.2)에는 내보내지 않는다
public record OrderLineResponse(
        Long orderItemId,
        Long itemId,
        String itemCode,
        String itemName,
        int quantity,
        long unitPrice,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long unitCost,
        long lineAmount
) {
    public static OrderLineResponse from(OrderItem orderItem, boolean includeCost) {
        return new OrderLineResponse(orderItem.getOrderItemId(), orderItem.getItem().getItemId(),
                orderItem.getItem().getItemCode(), orderItem.getItem().getItemName(), orderItem.getQuantity(),
                orderItem.getUnitPrice(), includeCost ? orderItem.getUnitCost() : null, orderItem.getLineAmount());
    }
}
