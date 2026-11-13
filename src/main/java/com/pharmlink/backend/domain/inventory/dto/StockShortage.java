package com.pharmlink.backend.domain.inventory.dto;

// 재고 부족(409 INSUFFICIENT_STOCK) 오류의 error.details 항목. available_quantity는 만료 로트를 제외한 출고 가능 수량
public record StockShortage(
        Long itemId,
        String itemName,
        int requestedQuantity,
        int availableQuantity
) {
}
