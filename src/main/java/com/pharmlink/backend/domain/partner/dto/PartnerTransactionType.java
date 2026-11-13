package com.pharmlink.backend.domain.partner.dto;

// 거래 이력 유형: 주문(ORDER), 매출 확정(SALE), 매입(PURCHASE)
// 고객사는 ORDER·SALE, 공급처는 PURCHASE만 발생
public enum PartnerTransactionType {
    ORDER, SALE, PURCHASE
}
