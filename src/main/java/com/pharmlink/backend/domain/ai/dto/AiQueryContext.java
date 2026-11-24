package com.pharmlink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// 서버가 실제로 실행한 조회 조건과 결과 건수
// FE는 total_count == 0 이면 '결과 없음'으로 표시한다.
public record AiQueryContext(
        String functionName,
        String keyword,
        Long warehouseId,
        int page,
        long totalCount,
        int totalPages,
        boolean hasNext,
        // 유통기한 조회에서만 사용
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer days,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String expiryStatus
) {

    public static AiQueryContext of(
            String functionName,
            String keyword,
            Long warehouseId,
            int page,
            long totalCount,
            int totalPages,
            boolean hasNext
    ) {
        return new AiQueryContext(
                functionName,
                keyword,
                warehouseId,
                page,
                totalCount,
                totalPages,
                hasNext,
                null,
                null
        );
    }
}
