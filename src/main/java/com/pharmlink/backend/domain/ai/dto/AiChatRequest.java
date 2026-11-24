package com.pharmlink.backend.domain.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiChatRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 1000, message = "질문은 1000자를 초과할 수 없습니다.")
        String message,

        // 선택 : 최근 대화 최대 10개 (오래된 것 → 최신 순)
        @Valid
        @Size(max = 10, message = "이전 대화는 최대 10개까지 보낼 수 있습니다.")
        List<AiChatHistory> history
) {

    public AiChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    public AiChatRequest(String message) {
        this(message, List.of());
    }
}
