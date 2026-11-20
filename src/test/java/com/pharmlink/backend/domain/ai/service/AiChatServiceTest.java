package com.pharmlink.backend.domain.ai.service;

import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiChatServiceTest {

    private final AiChatService aiChatService = new AiChatService();

    @Test
    void chatReturnsTestResponse() {
        AiChatRequest request =
                new AiChatRequest("재고 부족 상품 알려줘");

        AiChatResponse response = aiChatService.chat(request);

        assertEquals("TEST", response.intent());
        assertTrue(response.answer().contains("재고 부족 상품 알려줘"));
    }
}
