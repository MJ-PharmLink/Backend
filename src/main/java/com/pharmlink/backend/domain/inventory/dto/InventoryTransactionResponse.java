package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pharmlink.backend.domain.inventory.entity.InventoryTransaction;
import com.pharmlink.backend.domain.inventory.entity.ReferenceType;
import com.pharmlink.backend.domain.inventory.entity.TransactionType;

import java.time.Instant;

public record InventoryTransactionResponse(

        @JsonProperty("transaction_id")
        Long transactionId,

        @JsonProperty("type")
        TransactionType type,

        @JsonProperty("reference_type")
        ReferenceType referenceType,

        @JsonProperty("reference_id")
        Long referenceId,

        @JsonProperty("warehouse_id")
        Long warehouseId,

        @JsonProperty("item_id")
        Long itemId,

        @JsonProperty("lot_id")
        Long lotId,

        @JsonProperty("quantity")
        int quantity,

        @JsonProperty("quantity_after")
        int quantityAfter,

        @JsonProperty("reason")
        String reason,

        @JsonProperty("memo")
        String memo,

        @JsonProperty("created_by")
        Long createdBy,

        @JsonProperty("created_at")
        Instant createdAt
) {

    public static InventoryTransactionResponse from(
            InventoryTransaction transaction
    ) {
        return new InventoryTransactionResponse(
                transaction.getTransactionId(),
                transaction.getType(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getWarehouseId(),
                transaction.getItemId(),
                transaction.getLotId(),
                transaction.getQuantity(),
                transaction.getQuantityAfter(),
                transaction.getReason(),
                transaction.getMemo(),
                transaction.getCreatedBy(),
                transaction.getCreatedAt()
        );
    }
}
