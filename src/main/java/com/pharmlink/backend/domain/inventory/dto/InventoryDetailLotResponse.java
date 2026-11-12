package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.service.InventoryStatusCalculator;

import java.time.Instant;
import java.time.LocalDate;

public record InventoryDetailLotResponse(

        @JsonProperty("lot_id")
        Long lotId,

        @JsonProperty("lot_number")
        String lotNumber,

        @JsonProperty("expiry_date")
        LocalDate expiryDate,

        @JsonProperty("quantity")
        int quantity,

        @JsonProperty("expiry_status")
        String expiryStatus,

        @JsonProperty("received_at")
        Instant receivedAt
) {

    public static InventoryDetailLotResponse from(
            InventoryLot lot,
            LocalDate today,
            int expiringSoonDays
    ) {
        return new InventoryDetailLotResponse(
                lot.getLotId(),
                lot.getLotNumber(),
                lot.getExpiryDate(),
                lot.getQuantity(),
                InventoryStatusCalculator.expiryStatus(
                        lot.getExpiryDate(),
                        today,
                        expiringSoonDays
                ),
                lot.getReceivedAt()
        );
    }
}
