package com.pharmlink.backend.domain.purchase.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pharmlink.backend.domain.purchase.entity.PurchaseItem;

import java.time.LocalDate;

// 매입 라인 응답. quantity_after(입고 직후 재고 수량)는 등록 응답(10.2)에만 포함한다
public record PurchaseLineResponse(
        Long purchaseItemId,
        Long itemId,
        String itemCode,
        String itemName,
        int quantity,
        long unitCost,
        long lineAmount,
        Long lotId,
        String lotNumber,
        LocalDate expiryDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer quantityAfter
) {
    public static PurchaseLineResponse from(PurchaseItem purchaseItem, Integer quantityAfter) {
        return new PurchaseLineResponse(purchaseItem.getPurchaseItemId(), purchaseItem.getItem().getItemId(),
                purchaseItem.getItem().getItemCode(), purchaseItem.getItem().getItemName(), purchaseItem.getQuantity(),
                purchaseItem.getUnitCost(), purchaseItem.getLineAmount(), purchaseItem.getLot().getLotId(),
                purchaseItem.getLot().getLotNumber(), purchaseItem.getLot().getExpiryDate(), quantityAfter);
    }
}
