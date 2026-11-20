package com.pharmlink.backend.domain.ai.dto;

public record AiChatResponse(
        String answer,
        String intent
) {
}
