package com.pharmlink.backend.domain.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

// 주문 등록 요청 (8.2). 필수 누락·items 비어 있음은 400, 수량 1 미만·동일 상품 중복은 서비스에서 422 INVALID_QUANTITY
public record OrderCreateRequest(
        @NotNull Long partnerId,
        @NotEmpty List<@NotNull @Valid Line> items
) {
    public record Line(
            @NotNull Long itemId,
            @NotNull Integer quantity
    ) {
    }
}
