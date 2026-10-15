package com.pharmlink.backend.domain.purchase.dto;

import com.pharmlink.backend.domain.purchase.entity.Purchase;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

// 매입 등록 응답 (10.2)
public record PurchaseCreateResponse(
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
    // quantityAfter는 라인 순서대로 입고 직후의 재고 수량
    public static PurchaseCreateResponse from(Purchase purchase, List<Integer> quantityAfter) {
        List<PurchaseLineResponse> lines = IntStream.range(0, purchase.getItems().size())
                .mapToObj(i -> PurchaseLineResponse.from(purchase.getItems().get(i), quantityAfter.get(i)))
                .toList();
        return new PurchaseCreateResponse(purchase.getPurchaseId(), purchase.getPartner().getPartnerId(),
                purchase.getPartner().getName(), purchase.getWarehouse().getWarehouseId(), purchase.getPurchaseDate(),
                lines, purchase.getTotalAmount(), purchase.getCreatedBy(), purchase.getCreatedAt());
    }
}
