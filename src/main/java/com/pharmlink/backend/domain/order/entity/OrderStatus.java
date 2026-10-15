package com.pharmlink.backend.domain.order.entity;

// 주문 상태: PENDING(승인 대기) → APPROVED(승인·재고 차감 완료) / CANCELLED(취소, PENDING에서만 전이)
public enum OrderStatus {
    PENDING, APPROVED, CANCELLED
}
