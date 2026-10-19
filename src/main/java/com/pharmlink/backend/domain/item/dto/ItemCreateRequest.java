package com.pharmlink.backend.domain.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ItemCreateRequest(

        @NotBlank(message = "상품 코드는 필수입니다.")
        @Size(max = 20, message = "상품 코드는 20자 이하여야 합니다.")
        String itemCode,

        @NotBlank(message = "상품명은 필수입니다.")
        @Size(max = 100, message = "상품명은 100자 이하여야 합니다.")
        String itemName,

        @NotNull(message = "카테고리 ID는 필수입니다.")
        Long categoryId,

        @Size(max = 100, message = "상품 규격은 100자 이하여야 합니다.")
        String spec,

        @NotBlank(message = "상품 단위는 필수입니다.")
        @Size(max = 20, message = "상품 단위는 20자 이하여야 합니다.")
        String unit,

        @NotNull(message = "매입 단가는 필수입니다.")
        @Min(value = 0, message = "매입 단가는 0 이상이어야 합니다.")
        Long unitCost,

        @NotNull(message = "판매 단가는 필수입니다.")
        @Min(value = 0, message = "판매 단가는 0 이상이어야 합니다.")
        Long unitPrice,

        @NotNull(message = "안전재고는 필수입니다.")
        @Min(value = 0, message = "안전재고는 0 이상이어야 합니다.")
        Integer safetyStock,

        @NotNull(message = "공급처 ID는 필수입니다.")
        Long supplierId

) {
}

