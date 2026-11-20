package com.pharmlink.backend.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AiChatRequest(
        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 1000, message = "질문은 1000자를 초과할 수 없습니다.")
        String message
) {
}
