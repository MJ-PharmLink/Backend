package com.pharmlink.backend.domain.purchase.dto;

import com.pharmlink.backend.domain.purchase.entity.Purchase;

import java.time.Instant;
import java.time.LocalDate;

// 매입 목록 응답 항목 (10.1). item_count는 매입 라인 수
public record PurchaseResponse(
        Long purchaseId,
        Long partnerId,
        String partnerName,
        Long warehouseId,
        LocalDate purchaseDate,
        long totalAmount,
        int itemCount,
        Long createdBy,
        Instant createdAt
) {
    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(purchase.getPurchaseId(), purchase.getPartner().getPartnerId(),
                purchase.getPartner().getName(), purchase.getWarehouse().getWarehouseId(), purchase.getPurchaseDate(),
                purchase.getTotalAmount(), purchase.getItems().size(), purchase.getCreatedBy(),
                purchase.getCreatedAt());
    }
}
