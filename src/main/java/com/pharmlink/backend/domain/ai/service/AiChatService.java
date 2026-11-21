package com.pharmlink.backend.domain.ai.service;

import com.pharmlink.backend.domain.ai.client.GeminiLlmClient;
import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
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

    private final GeminiLlmClient geminiLlmClient;
    private final AiInventoryTool aiInventoryTool;

    public AiChatResponse chat(AiChatRequest request) {
        JsonNode modelContent =
                geminiLlmClient.requestFunctionDecision(request.message());

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

            return new AiChatResponse(
                    generalAnswer.toString(),
                    "GENERAL"
            );
        }

        String functionName = selectedFunction.path("name").asText();

        if (!LOW_STOCK_FUNCTION.equals(functionName)) {
            throw aiServiceError();
        }

        JsonNode args = selectedFunction.path("args");

        if (!args.isObject()) {
            throw aiServiceError();
        }

        Long warehouseId = readWarehouseId(args.path("warehouse_id"));
        Integer page = readPage(args.path("page"));

        AiInventoryTool.LowStockResult result =
                aiInventoryTool.getLowStockItems(warehouseId, page);

        String answer = geminiLlmClient.generateFunctionAnswer(
                request.message(),
                modelContent,
                result
        );

        return new AiChatResponse(answer, "LOW_STOCK");
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
