package com.pharmlink.backend.domain.ai.service;

import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
import org.springframework.stereotype.Service;

@Service
public class AiChatService {

    public AiChatResponse chat(AiChatRequest request) {
        String answer = "PharmLink AI 챗봇 연결 테스트 성공: "
                + request.message();

        return new AiChatResponse(answer, "TEST");
    }
}
