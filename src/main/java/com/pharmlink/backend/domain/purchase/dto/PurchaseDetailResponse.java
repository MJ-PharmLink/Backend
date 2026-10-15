package com.pharmlink.backend.domain.purchase.dto;

import com.pharmlink.backend.domain.purchase.entity.Purchase;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// 매입 상세 응답 (10.3). unit_cost는 매입 시점 스냅샷, quantity_after는 포함하지 않는다
public record PurchaseDetailResponse(
        Long purchaseId,
        Long partnerId,
        String partnerName,
        Long warehouseId,
        LocalDate purchaseDate,
        List<PurchaseLineResponse> items,
        long totalAmount,
        Long createdBy,
        Instant createdAt
) {
    public static PurchaseDetailResponse from(Purchase purchase) {
        return new PurchaseDetailResponse(purchase.getPurchaseId(), purchase.getPartner().getPartnerId(),
                purchase.getPartner().getName(), purchase.getWarehouse().getWarehouseId(), purchase.getPurchaseDate(),
                purchase.getItems().stream().map(item -> PurchaseLineResponse.from(item, null)).toList(),
                purchase.getTotalAmount(), purchase.getCreatedBy(), purchase.getCreatedAt());
    }
}
