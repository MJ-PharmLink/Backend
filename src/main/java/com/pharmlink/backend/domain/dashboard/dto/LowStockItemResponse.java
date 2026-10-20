package com.pharmlink.backend.domain.dashboard.dto;

// 재고 부족 품목 (12.2). stock_status는 7.1과 같은 규칙: 수량 0 → OUT_OF_STOCK, 안전재고 이하 → LOW_STOCK
public record LowStockItemResponse(
        Long inventoryId,
        Long warehouseId,
        Long itemId,
        String itemCode,
        String itemName,
        int quantity,
        int safetyStock,
        int shortageQuantity,
        String stockStatus
) {
    public LowStockItemResponse(Long inventoryId, Long warehouseId, Long itemId, String itemCode, String itemName,
                                int quantity, int safetyStock) {
        this(inventoryId, warehouseId, itemId, itemCode, itemName, quantity, safetyStock, safetyStock - quantity,
                quantity == 0 ? "OUT_OF_STOCK" : "LOW_STOCK");
    }
}
