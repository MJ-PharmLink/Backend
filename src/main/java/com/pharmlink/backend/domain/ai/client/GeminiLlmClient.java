package com.pharmlink.backend.domain.ai.client;

import com.pharmlink.backend.domain.ai.dto.AiChatHistory;
import tools.jackson.databind.JsonNode;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class GeminiLlmClient {

    // 호출당 읽기 제한 : 12초 (ai.gemini.read-timeout 으로 변경 가능)
    // 운영 CloudFront는 응답을 30초까지만 기다리므로, 한 요청의 Gemini 호출 2번(의도 판단, 답변)이
    // 30초 안에 끝나도록 둔다. 넘으면 504 AI_TIMEOUT.
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private final RestClient restClient;
    private final String model;
    private final boolean apiKeyConfigured;

    // 테스트용 : 기본 읽기 제한(12초)
    GeminiLlmClient(String apiKey, String model, String baseUrl) {
        this(apiKey, model, baseUrl, DEFAULT_READ_TIMEOUT);
    }

    @Autowired
    public GeminiLlmClient(
            // 키가 없어도 서버는 떠야 한다(CI 테스트, 운영 키 누락) : 그때는 챗봇 API만 503 AI_SERVICE_UNAVAILABLE
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
            String baseUrl,
            @Value("${ai.gemini.read-timeout:12s}") Duration readTimeout
    ) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build()
        );
        factory.setReadTimeout(readTimeout);

        this.apiKeyConfigured = apiKey != null && !apiKey.isBlank();

        if (!apiKeyConfigured) {
            log.warn("Gemini API 키가 없습니다 : 챗봇 API는 503을 돌려줍니다. 로컬은 application-secret.yml 의 ai.gemini.api-key, 운영은 환경변수 GEMINI_API_KEY 를 확인하세요.");
        }

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);

        if (apiKeyConfigured) {
            builder.defaultHeader("x-goog-api-key", apiKey);
        }

        this.restClient = builder.build();

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

        JsonNode response = postGenerateContent(requestBody);

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
        return requestFunctionDecision(message, List.of());
    }

    public JsonNode requestFunctionDecision(
            String message,
            List<AiChatHistory> history
    ) {
        String systemPrompt = """
                당신의 이름은 팜봇이며, PharmLink 제약 유통 ERP의 AI 도우미입니다.
                사용자가 이름을 물으면 팜봇이라고 답변하세요.
                항상 한국어로 정확하고 간결하게 답변하세요.

                [실제 ERP 화면 사용 안내]
                관리자 재고 조정 절차:
                왼쪽 메뉴 재고 관리 → 오른쪽 위 재고 조정 버튼
                → 상품, 로트, 구분(증가/감소), 수량, 사유, 비고 입력
                → 조정 반영.
                사유는 파손, 만료 폐기, 실사 보정, 반품 입고, 기타 중 선택합니다.
                재고 조정은 관리자 전용 기능입니다.

                [응답 제한]
                확인되지 않은 메뉴, 버튼, 화면, 기능을 만들어 안내하지 마세요.
                실제 화면 구조를 모르면 해당 메뉴 화면을 확인하도록 안내하세요.
                팜봇은 조회 전용이므로 직접 데이터 변경을 수행하거나 제안하지 마세요.
                사용자가 수정 방법을 물으면 확인된 화면 사용 방법만 안내하세요.
                제조사, 공급업체, 거래처 목록은 현재 조회 도구가 없으므로
                실제 목록을 조회할 수 없다고 안내하세요.
                한 번의 응답에서 조회 함수는 최대 하나만 호출하세요.
                여러 독립적인 조회를 동시에 요청하면
                한 번에 한 가지씩 질문해 달라고 안내하세요.

                [ERP 데이터 조회]
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
                유통기한이 지났거나 곧 끝나는 재고·LOT를 물으면
                get_expiring_inventory_items 함수를 사용하세요.
                "N일 이내"는 days 인자로, 만료만 물으면 expiry_status를 EXPIRED로,
                임박만 물으면 EXPIRING_SOON으로 전달하세요. 기간이 없으면 생략하세요.
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
                "contents", toContents(history, message),
                "tools", List.of(
                        Map.of(
                                "functionDeclarations",
                                List.of(declaration, inventoryDeclaration, expiryDeclaration())
                        )
                ),
                "generationConfig", Map.of(
                        "maxOutputTokens", 1024
                )
        );

        JsonNode response = postGenerateContent(requestBody);

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

        JsonNode response = postGenerateContent(requestBody);

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

    // Gemini contents : 이전 대화(assistant → model) + 현재 질문
    static List<Map<String, Object>> toContents(
            List<AiChatHistory> history,
            String message
    ) {
        List<Map<String, Object>> contents = new ArrayList<>();

        if (history != null) {
            for (AiChatHistory turn : history) {
                contents.add(Map.of(
                        "role", "assistant".equals(turn.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", turn.content()))
                ));
            }
        }

        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", message))
        ));

        return contents;
    }

    // 유통기한 만료·임박 LOT 조회 (기존 GET /api/v1/inventories/expiring 과 같은 조건)
    static Map<String, Object> expiryDeclaration() {
        return Map.of(
                "name", "get_expiring_inventory_items",
                "description",
                "실제 ERP에서 유통기한이 지났거나 곧 끝나는 LOT 재고를 조회합니다. "
                + "유통기한 만료, 유통기한 임박, N일 안에 유통기한이 끝나는 재고를 물을 때 사용합니다.",
                "parameters", Map.of(
                        "type", "OBJECT",
                        "properties", Map.of(
                                "days", Map.of(
                                        "type", "INTEGER",
                                        "description",
                                        "오늘부터 며칠 이내에 유통기한이 끝나는지. 기본값 90, 최대 365"
                                ),
                                "expiry_status", Map.of(
                                        "type", "STRING",
                                        "enum", List.of("EXPIRED", "EXPIRING_SOON"),
                                        "description",
                                        "EXPIRED: 이미 만료, EXPIRING_SOON: 임박. 둘 다면 생략"
                                ),
                                "warehouse_id", Map.of(
                                        "type", "INTEGER",
                                        "description", "조회할 창고 ID. 없으면 생략"
                                ),
                                "page", Map.of(
                                        "type", "INTEGER",
                                        "description", "1부터 시작하는 페이지 번호. 기본값 1"
                                )
                        )
                )
        );
    }

    // Gemini 호출 공통 처리 : HTTP 상태와 예외 종류에 따라 429/503/504로 나눈다.
    private JsonNode postGenerateContent(Map<String, Object> requestBody) {
        if (!apiKeyConfigured) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        JsonNode response;

        try {
            response = restClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .body(requestBody)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpStatusCodeException e) {
            // 운영 원인 추적용 : Gemini 상태 코드만 남긴다 (요청 본문·키는 남기지 않음)
            ErrorCode code = mapStatus(e.getStatusCode().value());
            log.warn("Gemini 호출 실패 : HTTP {} → {}", e.getStatusCode().value(), code);
            throw new BusinessException(code);
        } catch (RestClientException e) {
            ErrorCode code = isTimeout(e) ? ErrorCode.AI_TIMEOUT : ErrorCode.AI_SERVICE_UNAVAILABLE;
            log.warn("Gemini 호출 실패 : {} → {}", e.getClass().getSimpleName(), code);
            throw new BusinessException(code);
        }

        if (response == null) {
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        return response;
    }

    static ErrorCode mapStatus(int status) {
        if (status == 429) {
            return ErrorCode.AI_RATE_LIMITED;
        }

        if (status == 504) {
            return ErrorCode.AI_TIMEOUT;
        }

        return ErrorCode.AI_SERVICE_UNAVAILABLE;
    }

    static boolean isTimeout(Throwable e) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof HttpTimeoutException
                    || cause instanceof SocketTimeoutException
                    || cause instanceof TimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }

        return false;
    }
}
