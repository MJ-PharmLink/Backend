
package com.pharmlink.backend.domain.item.dto;

import com.pharmlink.backend.domain.item.entity.Item;

import java.time.Instant;

public record ItemResponse(
        Long itemId,
        String itemCode,
        String itemName,
        Long categoryId,
        String spec,
        String unit,
        Long unitCost,
        Long unitPrice,
        Integer safetyStock,
        Long supplierId,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {

    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getItemId(),
                item.getItemCode(),
                item.getItemName(),
                item.getCategoryId(),
                item.getSpec(),
                item.getUnit(),
                item.getUnitCost(),
                item.getUnitPrice(),
                item.getSafetyStock(),
                item.getSupplierId(),
                item.isActive(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

