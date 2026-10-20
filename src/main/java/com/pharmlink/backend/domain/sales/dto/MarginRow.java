package com.pharmlink.backend.domain.sales.dto;

// 마진 집계(11.3) 쿼리 결과 한 줄. id·code·name은 그룹(거래처 또는 상품) 식별 정보이고, 전체 합계에서는 null
public record MarginRow(
        Long id,
        String code,
        String name,
        Long salesAmount,
        Long costAmount
) {
    public long sales() {
        return salesAmount == null ? 0 : salesAmount;
    }

    public long cost() {
        return costAmount == null ? 0 : costAmount;
    }
}
