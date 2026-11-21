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

    public JsonNode requestFunctionDecision(String message) {
        String systemPrompt = """
                당신은 PharmLink 제약 유통 ERP의 AI 도우미입니다.
                재고 부족 상품의 실제 데이터가 필요한 질문에는
                get_low_stock_items 함수를 사용하세요.
                창고 번호가 명시되지 않았으면 전체 창고를 조회하세요.
                페이지가 명시되지 않았으면 첫 페이지를 조회하세요.
                실제 데이터는 추측하거나 만들어내지 마세요.
                일반적인 ERP 사용법 질문에는 한국어로 답변하세요.
                일반 상품 재고나 특정 창고의 재고를 물으면
                get_inventory_items 함수를 사용하세요.
                상품명 또는 상품코드는 keyword 인자로 전달하세요.
                부족 재고만 물으면 get_low_stock_items를 사용하세요.
                재고 변경, 상품 삭제 등 데이터 변경은 수행할 수 없습니다.
                """;

        Map<String, Object> parameters = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "warehouse_id", Map.of(
                                "type", "INTEGER",
                                "description", "조회할 창고 ID. 없으면 생략"
                        ),
                        "page", Map.of(
                                "type", "INTEGER",
                                "description", "1부터 시작하는 페이지 번호. 기본값 1"
                        )
                )
        );

        Map<String, Object> declaration = Map.of(
                "name", "get_low_stock_items",
                "description",
                "실제 ERP에서 안전재고 이하인 상품을 조회합니다. "
                + "현재 재고가 부족하거나 품절된 상품을 물을 때 사용합니다.",
                "parameters", parameters
        );

        Map<String, Object> inventoryDeclaration = Map.of(
                "name", "get_inventory_items",
                "description",
                "실제 ERP의 상품별 또는 창고별 재고를 조회합니다. "
                + "상품명, 상품코드, 창고 번호로 재고를 검색할 때 사용합니다. "
                + "부족 재고만 요청하는 경우에는 get_low_stock_items를 사용하세요.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "keyword", Map.of(
                                        "type", "STRING",
                                        "description",
                                        "검색할 상품명 또는 상품코드. 없으면 생략"
                                ),
                                "warehouse_id", Map.of(
                                        "type", "INTEGER",
                                        "description",
                                        "조회할 창고 ID. 없으면 생략"
                                ),
                                "page", Map.of(
                                        "type", "INTEGER",
                                        "description",
                                        "1부터 시작하는 페이지 번호. 기본값 1"
                                )
                        )
                )
        );

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("text", message))
                        )
                ),
                "tools", List.of(
                        Map.of(
                                "functionDeclarations", List.of(declaration, inventoryDeclaration)
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
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        JsonNode content = response.path("candidates")
                .path(0)
                .path("content");

        if (!content.isObject() || !content.path("parts").isArray()
                || content.path("parts").isEmpty()) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        return content;
    }


    public String generateFunctionAnswer(
            String question,
            JsonNode modelContent,
            Object functionResult
    ) {
        return generateFunctionAnswer(
                question,
                modelContent,
                "get_low_stock_items",
                functionResult
        );
    }

    public String generateFunctionAnswer(
            String question,
            JsonNode modelContent,
            String functionName,
            Object functionResult
    ) {
        Map<String, Object> functionResponse = Map.of(
                "role", "user",
                "parts", List.of(
                        Map.of(
                                "functionResponse", Map.of(
                                        "name", functionName,
                                        "response", Map.of(
                                                "result", functionResult
                                        )
                                )
                        )
                )
        );

        Map<String, Object> requestBody = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(
                                Map.of(
                                        "text",
                                        """
                                        당신은 PharmLink ERP의 AI 도우미입니다.
                                        반드시 제공된 실제 조회 결과만 사용하세요.
                                        상품명과 재고 수량을 임의로 만들지 마세요.
                                        조회된 항목이 없으면 없다고 안내하세요.
                                        일부 페이지만 조회했다면 전체 결과인 것처럼 말하지 마세요.
                                        한국어로 간결하게 답변하세요.
                                        """
                                )
                        )
                ),
                "contents", List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of("text", question)
                                )
                        ),
                        modelContent,
                        functionResponse
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
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
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
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        return answer.toString();
    }

}
