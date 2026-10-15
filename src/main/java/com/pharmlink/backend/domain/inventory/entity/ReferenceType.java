package com.pharmlink.backend.domain.inventory.entity;

// 재고 이력의 발생 원인. PURCHASE → purchase_id, ORDER → order_id, ADJUSTMENT → reference_id 없음
public enum ReferenceType {
    PURCHASE, ORDER, ADJUSTMENT
}
