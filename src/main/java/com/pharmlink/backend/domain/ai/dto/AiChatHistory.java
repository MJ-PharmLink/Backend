package com.pharmlink.backend.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 이전 대화 한 턴 : role은 user 또는 assistant
// 과거 대화는 질문 의도 파악에만 쓰고, DB 조회는 항상 현재 사용자 인증·권한으로 다시 실행한다.
public record AiChatHistory(
        @NotBlank
        @Pattern(regexp = "user|assistant", message = "role은 user 또는 assistant만 가능합니다.")
        String role,

        @NotBlank
        @Size(max = 2000, message = "이전 대화 내용은 2000자를 초과할 수 없습니다.")
        String content
) {
}
