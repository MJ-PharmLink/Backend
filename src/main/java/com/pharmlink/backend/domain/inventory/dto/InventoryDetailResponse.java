package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record InventoryDetailResponse(

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

        @JsonProperty("item_status")
        String itemStatus,

        @JsonProperty("quantity")
        int quantity,

        @JsonProperty("available_quantity")
        int availableQuantity,

        @JsonProperty("safety_stock")
        int safetyStock,

        @JsonProperty("stock_status")
        String stockStatus,

        @JsonProperty("expiry_status")
        String expiryStatus,

        @JsonProperty("lots")
        List<InventoryDetailLotResponse> lots
) {

    public static InventoryDetailResponse from(
            InventoryResponse inventory,
            List<InventoryDetailLotResponse> lots
    ) {
        return new InventoryDetailResponse(
                inventory.inventoryId(),
                inventory.warehouseId(),
                inventory.warehouseName(),
                inventory.itemId(),
                inventory.itemCode(),
                inventory.itemName(),
                inventory.itemStatus(),
                inventory.quantity(),
                inventory.availableQuantity(),
                inventory.safetyStock(),
                inventory.stockStatus(),
                inventory.expiryStatus(),
                List.copyOf(lots)
        );
    }
}
