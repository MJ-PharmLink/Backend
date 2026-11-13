package com.pharmlink.backend.domain.partner.dto;

import java.time.Instant;

// 거래 이력 응답 항목. 각 행은 (type, reference_id) 조합으로 식별
// reference_id: ORDER면 order_id, SALE이면 sale_id, PURCHASE면 purchase_id
// reference_number: ORDER·SALE이면 주문번호, PURCHASE면 null
// status: ORDER 행만 주문 상태(PENDING/APPROVED/CANCELLED), SALE·PURCHASE는 null
public record PartnerTransactionResponse(
        PartnerTransactionType type,
        String status,
        Long referenceId,
        String referenceNumber,
        Long amount,
        Instant transactionDate
) {
}
