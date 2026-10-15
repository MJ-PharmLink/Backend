package com.pharmlink.backend.domain.inventory.entity;

// 재고 이력 유형: 입고(매입) / 출고(주문 승인) / 재고 조정 증가·감소
public enum TransactionType {
    IN, OUT, ADJUST_IN, ADJUST_OUT
}
