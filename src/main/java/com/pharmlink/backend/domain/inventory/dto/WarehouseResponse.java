package com.pharmlink.backend.domain.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import java.time.Instant;

public record WarehouseResponse(
        @JsonProperty("warehouse_id")
        Long warehouseId,

        @JsonProperty("name")
        String name,

        @JsonProperty("location")
        String location,

        @JsonProperty("is_default")
        boolean isDefault,

        @JsonProperty("created_at")
        Instant createdAt
) {
    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getWarehouseId(),
                warehouse.getName(),
                warehouse.getLocation(),
                warehouse.isDefault(),
                warehouse.getCreatedAt()
        );
    }
}
