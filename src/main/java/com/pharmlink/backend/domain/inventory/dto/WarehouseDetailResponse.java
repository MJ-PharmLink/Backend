package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;

public record WarehouseDetailResponse(
        @JsonProperty("warehouse_id") Long warehouseId,
        String name,
        String location,
        @JsonProperty("is_default") boolean isDefault,
        Summary summary
) {
    public record Summary(
            @JsonProperty("total_item_count") long totalItemCount,
            @JsonProperty("total_quantity") long totalQuantity,
            @JsonProperty("by_stock_status") StockStatus byStockStatus,
            @JsonProperty("by_item_status") ItemStatus byItemStatus,
            @JsonProperty("by_expiry_status") ExpiryStatus byExpiryStatus
    ) {}

    public record StockStatus(
            long normal,
            @JsonProperty("low_stock") long lowStock,
            @JsonProperty("out_of_stock") long outOfStock
    ) {}

    public record ItemStatus(
            long active,
            long discontinued
    ) {}

    public record ExpiryStatus(
            long normal,
            @JsonProperty("expiring_soon") long expiringSoon,
            long expired
    ) {}

    public static WarehouseDetailResponse from(
            Warehouse warehouse,
            Summary summary
    ) {
        return new WarehouseDetailResponse(
                warehouse.getWarehouseId(),
                warehouse.getName(),
                warehouse.getLocation(),
                warehouse.isDefault(),
                summary
        );
    }
}
