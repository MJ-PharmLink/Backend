package com.pharmlink.backend.domain.ai.service;

import com.pharmlink.backend.domain.ai.client.GeminiLlmClient;
import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
import com.pharmlink.backend.domain.ai.tool.AiInventoryTool;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generalQuestionReturnsGeneralAnswer() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        AiInventoryTool inventoryTool = mock(AiInventoryTool.class);

        String question = "PharmLink ERP가 무엇인가요?";

        JsonNode modelContent = mapper.readTree("""
                {
                  "role": "model",
                  "parts": [
                    {"text": "PharmLink는 제약 유통 ERP입니다."}
                  ]
                }
                """);

        when(client.requestFunctionDecision(question))
                .thenReturn(modelContent);

        AiChatService service = new AiChatService(client, inventoryTool);
        AiChatResponse response =
                service.chat(new AiChatRequest(question));

        assertEquals("GENERAL", response.intent());
        assertEquals(
                "PharmLink는 제약 유통 ERP입니다.",
                response.answer()
        );

        verify(inventoryTool, never())
                .getLowStockItems(any(), any());
    }

    @Test
    void lowStockQuestionExecutesInventoryTool() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        AiInventoryTool inventoryTool = mock(AiInventoryTool.class);

        String question = "1번 창고의 재고 부족 상품 알려줘";

        JsonNode modelContent = mapper.readTree("""
                {
                  "role": "model",
                  "parts": [
                    {
                      "functionCall": {
                        "name": "get_low_stock_items",
                        "args": {
                          "warehouse_id": 1,
                          "page": 1
                        }
                      }
                    }
                  ]
                }
                """);

        AiInventoryTool.LowStockResult result =
                new AiInventoryTool.LowStockResult(
                        List.of(),
                        0,
                        1,
                        0,
                        false
                );

        when(client.requestFunctionDecision(question))
                .thenReturn(modelContent);

        when(inventoryTool.getLowStockItems(1L, 1))
                .thenReturn(result);

        when(client.generateFunctionAnswer(
                eq(question),
                eq(modelContent),
                eq(result)
        )).thenReturn("1번 창고에 조회된 재고 부족 상품이 없습니다.");

        AiChatService service = new AiChatService(client, inventoryTool);
        AiChatResponse response =
                service.chat(new AiChatRequest(question));

        assertEquals("LOW_STOCK", response.intent());
        assertEquals(
                "1번 창고에 조회된 재고 부족 상품이 없습니다.",
                response.answer()
        );

        verify(inventoryTool).getLowStockItems(1L, 1);
        verify(client).generateFunctionAnswer(
                question,
                modelContent,
                result
        );
    }

    @Test
    void unknownFunctionIsRejected() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        AiInventoryTool inventoryTool = mock(AiInventoryTool.class);

        String question = "재고를 변경해줘";

        JsonNode modelContent = mapper.readTree("""
                {
                  "role": "model",
                  "parts": [
                    {
                      "functionCall": {
                        "name": "update_inventory",
                        "args": {}
                      }
                    }
                  ]
                }
                """);

        when(client.requestFunctionDecision(question))
                .thenReturn(modelContent);

        AiChatService service = new AiChatService(client, inventoryTool);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.chat(new AiChatRequest(question))
        );

        assertEquals(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );

        verify(inventoryTool, never())
                .getLowStockItems(any(), any());
    }

    @Test
    void inventoryQuestionExecutesInventorySearchTool() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        AiInventoryTool inventoryTool = mock(AiInventoryTool.class);

        String question = "1번 창고의 해열진통제 재고 알려줘";

        JsonNode modelContent = mapper.readTree("""
                {
                  "role": "model",
                  "parts": [
                    {
                      "functionCall": {
                        "name": "get_inventory_items",
                        "args": {
                          "keyword": "해열진통제",
                          "warehouse_id": 1,
                          "page": 1
                        }
                      }
                    }
                  ]
                }
                """);

        AiInventoryTool.InventorySearchResult result =
                new AiInventoryTool.InventorySearchResult(
                        List.of(),
                        0,
                        1,
                        0,
                        false
                );

        when(client.requestFunctionDecision(question))
                .thenReturn(modelContent);

        when(inventoryTool.getInventoryItems("해열진통제", 1L, 1))
                .thenReturn(result);

        when(client.generateFunctionAnswer(
                question,
                modelContent,
                "get_inventory_items",
                result
        )).thenReturn("1번 창고에 해당 상품의 재고가 없습니다.");

        AiChatService service = new AiChatService(client, inventoryTool);

        AiChatResponse response =
                service.chat(new AiChatRequest(question));

        assertEquals("INVENTORY", response.intent());
        assertEquals(
                "1번 창고에 해당 상품의 재고가 없습니다.",
                response.answer()
        );

        verify(inventoryTool).getInventoryItems("해열진통제", 1L, 1);
        verify(client).generateFunctionAnswer(
                question,
                modelContent,
                "get_inventory_items",
                result
        );
    }

}
