package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// API 명세서 7.4 — 재고 조정 요청 DTO
public record InventoryAdjustmentRequest(

        @NotNull
        @JsonProperty("warehouse_id")
        Long warehouseId,

        @NotNull
        @JsonProperty("item_id")
        Long itemId,

        @JsonProperty("lot_id")
        Long lotId,

        @Size(max = 50)
        @JsonProperty("lot_number")
        String lotNumber,

        @JsonProperty("expiry_date")
        LocalDate expiryDate,

        @NotBlank
        @JsonProperty("adjustment_type")
        String adjustmentType,

        @NotNull
        @JsonProperty("quantity")
        Integer quantity,

        @NotBlank
        @Size(max = 30)
        @JsonProperty("reason")
        String reason,

        @Size(max = 200)
        @JsonProperty("memo")
        String memo

) {
}
