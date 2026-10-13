package com.pharmlink.backend.domain.delivery.entity;

// 납품 상태: WAITING(출고대기) → SHIPPED(출고완료) → DELIVERED(납품완료, 매출 자동 생성)
public enum DeliveryStatus {
    WAITING, SHIPPED, DELIVERED
}
