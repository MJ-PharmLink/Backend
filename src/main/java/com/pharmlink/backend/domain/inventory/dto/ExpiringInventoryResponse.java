package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record ExpiringInventoryResponse(

        @JsonProperty("lot_id")
        Long lotId,

        @JsonProperty("inventory_id")
        Long inventoryId,

        @JsonProperty("warehouse_id")
        Long warehouseId,

        @JsonProperty("item_id")
        Long itemId,

        @JsonProperty("item_code")
        String itemCode,

        @JsonProperty("item_name")
        String itemName,

        @JsonProperty("lot_number")
        String lotNumber,

        @JsonProperty("expiry_date")
        LocalDate expiryDate,

        @JsonProperty("days_until_expiry")
        long daysUntilExpiry,

        @JsonProperty("quantity")
        int quantity,

        @JsonProperty("expiry_status")
        String expiryStatus
) {
}
