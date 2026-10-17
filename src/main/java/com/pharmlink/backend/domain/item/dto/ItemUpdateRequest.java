package com.pharmlink.backend.domain.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ItemUpdateRequest(

        @Size(max = 100)
        String itemName,

        Long categoryId,

        @Size(max = 100)
        String spec,

        @Size(max = 20)
        String unit,

        @Min(0)
        Long unitCost,

        @Min(0)
        Long unitPrice,

        @Min(0)
        Integer safetyStock,

        Long supplierId
) {
}

