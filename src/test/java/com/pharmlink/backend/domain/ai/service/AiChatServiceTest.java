package com.pharmlink.backend.domain.ai.service;

import com.pharmlink.backend.domain.ai.client.GeminiLlmClient;
import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    @Test
    void chatReturnsGeminiAnswer() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);

        String question = "PharmLink ERP가 무엇인가요?";
        String expectedAnswer = "PharmLink는 제약 유통업체를 위한 ERP입니다.";

        when(client.generateAnswer(question)).thenReturn(expectedAnswer);

        AiChatService service = new AiChatService(client);
        AiChatResponse response = service.chat(new AiChatRequest(question));

        assertEquals(expectedAnswer, response.answer());
        assertEquals("GENERAL", response.intent());

        verify(client).generateAnswer(question);
    }
}
