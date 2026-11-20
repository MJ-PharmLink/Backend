package com.pharmlink.backend.domain.ai.client;

import tools.jackson.databind.JsonNode;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.springframework.web.client.RestClientException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GeminiLlmClient {

    private final RestClient restClient;
    private final String model;

    public GeminiLlmClient(
            @Value("${ai.gemini.api-key}") String apiKey,
            @Value("${ai.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
            String baseUrl
    ) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(45));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .requestFactory(factory)
                .build();

        this.model = model;
    }

    public String generateAnswer(String message) {

        String systemPrompt = """
                당신은 PharmLink ERP의 AI 챗봇입니다.
                항상 한국어로 정확하고 간결하게 답변하세요.
                PharmLink는 제약 유통업체를 위한 ERP 시스템입니다.
                상품, 재고, 창고, LOT 및 유통기한 관리에 관한
                일반적인 사용 방법을 안내할 수 있습니다.
                실제 ERP 데이터베이스에 연결되어 있지 않은 상태에서는
                재고 수량, 상품 정보, 유통기한 등의 실제 값을
                추측하거나 만들어내지 마세요.
                실제 데이터 조회가 필요한 질문에는
                현재 조회할 수 없다고 안내하세요.
                """;

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(
                                Map.of("text", systemPrompt)
                        )
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", message)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "maxOutputTokens", 1024
                )
        );

        JsonNode response;

        try {
            response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        if (response == null) {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE
            );
        }

        JsonNode parts = response.path("candidates")
                .path(0)
                .path("content")
                .path("parts");

        StringBuilder answer = new StringBuilder();

        if (parts.isArray()) {
            for (JsonNode part : parts) {
                JsonNode text = part.path("text");
                if (text.isTextual()) {
                    answer.append(text.asText());
                }
            }
        }

        if (answer.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.AI_SERVICE_UNAVAILABLE
            );
        }

        return answer.toString();
    }
}
