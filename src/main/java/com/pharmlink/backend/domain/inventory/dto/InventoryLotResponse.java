package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;

import java.time.LocalDate;

public record InventoryLotResponse(

        @JsonProperty("lot_id")
        Long lotId,

        @JsonProperty("inventory_id")
        Long inventoryId,

        @JsonProperty("lot_number")
        String lotNumber,

        @JsonProperty("expiry_date")
        LocalDate expiryDate,

        @JsonProperty("quantity")
        int quantity
) {

    public static InventoryLotResponse from(InventoryLot lot) {
        return new InventoryLotResponse(
                lot.getLotId(),
                lot.getInventory().getInventoryId(),
                lot.getLotNumber(),
                lot.getExpiryDate(),
                lot.getQuantity()
        );
    }
}
