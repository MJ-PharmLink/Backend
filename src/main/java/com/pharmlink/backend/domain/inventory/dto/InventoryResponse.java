package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InventoryResponse(

        @JsonProperty("inventory_id")
        Long inventoryId,

        @JsonProperty("warehouse_id")
        Long warehouseId,

        @JsonProperty("warehouse_name")
        String warehouseName,

        @JsonProperty("item_id")
        Long itemId,

        @JsonProperty("item_code")
        String itemCode,

        @JsonProperty("item_name")
        String itemName,

        @JsonProperty("quantity")
        int quantity,

        @JsonProperty("safety_stock")
        int safetyStock,

        @JsonProperty("item_status")
        String itemStatus,

        @JsonProperty("available_quantity")
        int availableQuantity,

        @JsonProperty("stock_status")
        String stockStatus,

        @JsonProperty("lot_count")
        int lotCount,

        @JsonProperty("nearest_expiry_date")
        java.time.LocalDate nearestExpiryDate,

        @JsonProperty("expiry_status")
        String expiryStatus
) {
}
