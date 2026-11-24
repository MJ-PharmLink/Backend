package com.pharmlink.backend.domain.ai.dto;

import java.util.List;

// 답변 근거로 사용한 실제 ERP 조회 결과
// type : 실행한 함수 이름 (get_low_stock_items 등)
// title : 화면에 보여 줄 제목
// rows : 기존 ERP API 응답 DTO를 그대로 사용 (필드명 동일)
public record AiSource(
        String type,
        String title,
        List<?> rows
) {

    public AiSource {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
