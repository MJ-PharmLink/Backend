package com.pharmlink.backend.domain.ai.service;

import com.pharmlink.backend.domain.ai.client.GeminiLlmClient;
import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private final GeminiLlmClient geminiLlmClient;

    public AiChatResponse chat(AiChatRequest request) {
        String answer = geminiLlmClient.generateAnswer(request.message());

        return new AiChatResponse(answer, "GENERAL");
    }
}
