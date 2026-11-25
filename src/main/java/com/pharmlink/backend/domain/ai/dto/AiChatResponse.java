package com.pharmlink.backend.domain.ai.dto;

import java.time.Instant;
import java.util.List;

// 응답 JSON은 전역 SNAKE_CASE 설정으로 query_context, generated_at 으로 나간다.
// 일반 질문 : sources = [], query_context = null
// ERP 조회 질문 : 실제 조회 결과를 sources[0].rows 에, 조회 조건·건수를 query_context 에 담는다.
public record AiChatResponse(
        String answer,
        String intent,
        List<AiSource> sources,
        AiQueryContext queryContext,
        Instant generatedAt
) {

    public AiChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }

    // 조회 없이 답변만 있는 경우 (일반 질문)
    public AiChatResponse(String answer, String intent) {
        this(answer, intent, List.of(), null, null);
    }

    public static AiChatResponse general(String answer) {
        return new AiChatResponse(answer, "GENERAL");
    }

    public static AiChatResponse ofQuery(
            String answer,
            String intent,
            AiSource source,
            AiQueryContext queryContext
    ) {
        return new AiChatResponse(
                answer,
                intent,
                List.of(source),
                queryContext,
                null
        );
    }
}
