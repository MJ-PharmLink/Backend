package com.pharmlink.backend.domain.order.dto;

import jakarta.validation.constraints.Size;

// 주문 취소 요청 (8.5). 사유는 선택, 최대 200자
public record OrderCancelRequest(
        @Size(max = 200) String cancelReason
) {
}
