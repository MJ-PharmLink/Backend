package com.pharmlink.backend.domain.ai.service;

import com.pharmlink.backend.domain.ai.client.GeminiLlmClient;
import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
import com.pharmlink.backend.domain.ai.dto.AiQueryContext;
import com.pharmlink.backend.domain.ai.dto.AiSource;
import com.pharmlink.backend.domain.ai.tool.AiExpiryTool;
import com.pharmlink.backend.domain.ai.tool.AiInventoryTool;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String LOW_STOCK_FUNCTION = "get_low_stock_items";
    private static final String INVENTORY_FUNCTION = "get_inventory_items";
    private static final String EXPIRY_FUNCTION = "get_expiring_inventory_items";

    private final GeminiLlmClient geminiLlmClient;
    private final AiInventoryTool aiInventoryTool;
    private final AiExpiryTool aiExpiryTool;

    public AiChatResponse chat(AiChatRequest request) {
        // history가 없으면 기존 흐름 그대로, 있으면 이전 대화를 함께 보내 의도를 판단한다.
        JsonNode modelContent = request.history().isEmpty()
                ? geminiLlmClient.requestFunctionDecision(request.message())
                : geminiLlmClient.requestFunctionDecision(
                        request.message(),
                        request.history()
                );

        JsonNode parts = modelContent.path("parts");

        if (!parts.isArray() || parts.isEmpty()) {
            throw aiServiceError();
        }

        JsonNode selectedFunction = null;
        StringBuilder generalAnswer = new StringBuilder();

        for (JsonNode part : parts) {
            JsonNode functionCall = part.path("functionCall");

            if (functionCall.isObject()) {
                if (selectedFunction != null) {
                    throw aiServiceError();
                }
                selectedFunction = functionCall;
            }

            JsonNode text = part.path("text");

            if (text.isTextual()) {
                generalAnswer.append(text.asText());
            }
        }

        if (selectedFunction == null) {
            if (generalAnswer.toString().isBlank()) {
                throw aiServiceError();
            }

            return AiChatResponse.general(generalAnswer.toString());
        }

        String functionName = selectedFunction.path("name").asText();

        if (!LOW_STOCK_FUNCTION.equals(functionName)
                && !INVENTORY_FUNCTION.equals(functionName)
                && !EXPIRY_FUNCTION.equals(functionName)) {
            throw aiServiceError();
        }

        JsonNode args = selectedFunction.path("args");

        if (!args.isObject()) {
            throw aiServiceError();
        }

        Long warehouseId = readWarehouseId(args.path("warehouse_id"));
        Integer page = readPage(args.path("page"));

        if (EXPIRY_FUNCTION.equals(functionName)) {
            return answerExpiry(request, modelContent, args, warehouseId, page);
        }

        if (LOW_STOCK_FUNCTION.equals(functionName)) {
            AiInventoryTool.LowStockResult result =
                    aiInventoryTool.getLowStockItems(warehouseId, page);

            String answer = geminiLlmClient.generateFunctionAnswer(
                    request.message(),
                    modelContent,
                    result
            );

            return AiChatResponse.ofQuery(
                    answer,
                    "LOW_STOCK",
                    new AiSource(
                            LOW_STOCK_FUNCTION,
                            lowStockTitle(warehouseId),
                            result.items()
                    ),
                    AiQueryContext.of(
                            LOW_STOCK_FUNCTION,
                            null,
                            warehouseId,
                            result.page(),
                            result.totalCount(),
                            result.totalPages(),
                            result.hasNext()
                    )
            );
        }

        String keyword = readKeyword(args.path("keyword"));

        AiInventoryTool.InventorySearchResult result =
                aiInventoryTool.getInventoryItems(keyword, warehouseId, page);

        String answer = geminiLlmClient.generateFunctionAnswer(
                request.message(),
                modelContent,
                INVENTORY_FUNCTION,
                result
        );

        return AiChatResponse.ofQuery(
                answer,
                "INVENTORY",
                new AiSource(
                        INVENTORY_FUNCTION,
                        inventoryTitle(keyword, warehouseId),
                        result.items()
                ),
                AiQueryContext.of(
                        INVENTORY_FUNCTION,
                        keyword,
                        warehouseId,
                        result.page(),
                        result.totalCount(),
                        result.totalPages(),
                        result.hasNext()
                )
        );
    }

    private AiChatResponse answerExpiry(
            AiChatRequest request,
            JsonNode modelContent,
            JsonNode args,
            Long warehouseId,
            Integer page
    ) {
        Integer days = readDays(args.path("days"));
        String expiryStatus = readExpiryStatus(args.path("expiry_status"));

        AiExpiryTool.ExpiringResult result =
                aiExpiryTool.getExpiringInventoryItems(
                        days,
                        expiryStatus,
                        warehouseId,
                        page
                );

        String answer = geminiLlmClient.generateFunctionAnswer(
                request.message(),
                modelContent,
                EXPIRY_FUNCTION,
                result
        );

        return AiChatResponse.ofQuery(
                answer,
                "EXPIRY",
                new AiSource(
                        EXPIRY_FUNCTION,
                        expiryTitle(),
                        result.items()
                ),
                new AiQueryContext(
                        EXPIRY_FUNCTION,
                        null,
                        warehouseId,
                        result.page(),
                        result.totalCount(),
                        result.totalPages(),
                        result.hasNext(),
                        result.days(),
                        result.expiryStatus()
                )
        );
    }

    private String expiryTitle() {
        return "유통기한 임박 재고";
    }

    private Integer readDays(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw aiServiceError();
        }

        int days = value.intValue();

        if (days < 0) {
            throw aiServiceError();
        }

        return Math.min(days, AiExpiryTool.MAX_DAYS);
    }

    private String readExpiryStatus(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        if (!value.isTextual()) {
            throw aiServiceError();
        }

        String status = value.asText().trim().toUpperCase();

        if (status.isBlank() || "ALL".equals(status)) {
            return null;
        }

        if (!"EXPIRED".equals(status) && !"EXPIRING_SOON".equals(status)) {
            throw aiServiceError();
        }

        return status;
    }

    private String warehouseLabel(Long warehouseId) {
        return warehouseId == null ? "전체 창고" : "창고 " + warehouseId;
    }

    // sources[].title 문구 : 부족 재고 (전체 창고 | 창고 N), 상품 재고 조회, 창고별 재고 조회, 유통기한 임박 재고
    private String lowStockTitle(Long warehouseId) {
        return "부족 재고 (" + warehouseLabel(warehouseId) + ")";
    }

    private String inventoryTitle(String keyword, Long warehouseId) {
        return keyword == null && warehouseId != null
                ? "창고별 재고 조회"
                : "상품 재고 조회";
    }

    private String readKeyword(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        if (!value.isTextual()) {
            throw aiServiceError();
        }

        String keyword = value.asText().trim();

        if (keyword.length() > 100) {
            throw aiServiceError();
        }

        return keyword.isBlank() ? null : keyword;
    }

    private Long readWarehouseId(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }

        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw aiServiceError();
        }

        long warehouseId = value.longValue();

        if (warehouseId < 1) {
            throw aiServiceError();
        }

        return warehouseId;
    }

    private Integer readPage(JsonNode value) {
        if (value.isMissingNode() || value.isNull()) {
            return 1;
        }

        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw aiServiceError();
        }

        int page = value.intValue();

        if (page < 1) {
            throw aiServiceError();
        }

        return Math.min(page, 100);
    }

    private BusinessException aiServiceError() {
        return new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }
}
