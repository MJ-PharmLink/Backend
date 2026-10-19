package com.pharmlink.backend.domain.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

// 매입 등록 요청 (10.2). 필수 누락·날짜 형식 오류는 400
// 수량 1 미만·동일 (상품, 로트번호) 중복은 422 INVALID_QUANTITY, 유통기한이 매입일 이전이면 422 INVALID_EXPIRY_DATE(같은 날은 허용)
public record PurchaseCreateRequest(
        @NotNull Long partnerId,
        // 생략하면 기본 창고 (is_default = true)
        Long warehouseId,
        @NotNull LocalDate purchaseDate,
        @NotEmpty List<@NotNull @Valid Line> items
) {
    public record Line(
            @NotNull Long itemId,
            @NotNull Integer quantity,
            @NotBlank @Size(max = 50) String lotNumber,
            @NotNull LocalDate expiryDate
    ) {
    }
}
