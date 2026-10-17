package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pharmlink.backend.domain.inventory.entity.Inventory;

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
        int safetyStock
) {

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getInventoryId(),
                inventory.getWarehouse().getWarehouseId(),
                inventory.getWarehouse().getName(),
                inventory.getItem().getItemId(),
                inventory.getItem().getItemCode(),
                inventory.getItem().getItemName(),
                inventory.getQuantity(),
                inventory.getItem().getSafetyStock()
        );
    }
}
