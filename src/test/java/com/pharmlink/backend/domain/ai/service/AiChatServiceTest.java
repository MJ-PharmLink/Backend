package com.pharmlink.backend.domain.ai.service;

import com.pharmlink.backend.domain.ai.client.GeminiLlmClient;
import com.pharmlink.backend.domain.ai.dto.AiChatRequest;
import com.pharmlink.backend.domain.ai.dto.AiChatResponse;
import com.pharmlink.backend.domain.ai.tool.AiExpiryTool;
import com.pharmlink.backend.domain.ai.tool.AiInventoryTool;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

        AiChatService service = new AiChatService(client, inventoryTool, mock(AiExpiryTool.class));
        AiChatResponse response =
                service.chat(new AiChatRequest(question));

        assertEquals("GENERAL", response.intent());
        assertEquals(
                "PharmLink는 제약 유통 ERP입니다.",
                response.answer()
        );

        assertTrue(response.sources().isEmpty());
        assertNull(response.queryContext());
        assertNotNull(response.generatedAt());

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

        AiChatService service = new AiChatService(client, inventoryTool, mock(AiExpiryTool.class));
        AiChatResponse response =
                service.chat(new AiChatRequest(question));

        assertEquals("LOW_STOCK", response.intent());
        assertEquals(
                "1번 창고에 조회된 재고 부족 상품이 없습니다.",
                response.answer()
        );

        assertEquals(1, response.sources().size());
        assertEquals("get_low_stock_items", response.sources().get(0).type());
        assertEquals("get_low_stock_items", response.queryContext().functionName());
        assertEquals(1L, response.queryContext().warehouseId());
        assertEquals(0L, response.queryContext().totalCount());
        assertFalse(response.queryContext().hasNext());

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

        AiChatService service = new AiChatService(client, inventoryTool, mock(AiExpiryTool.class));

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

        AiChatService service = new AiChatService(client, inventoryTool, mock(AiExpiryTool.class));

        AiChatResponse response =
                service.chat(new AiChatRequest(question));

        assertEquals("INVENTORY", response.intent());
        assertEquals(
                "1번 창고에 해당 상품의 재고가 없습니다.",
                response.answer()
        );

        assertEquals("get_inventory_items", response.sources().get(0).type());
        assertEquals("해열진통제", response.queryContext().keyword());
        assertEquals(1L, response.queryContext().warehouseId());
        assertEquals(0L, response.queryContext().totalCount());

        verify(inventoryTool).getInventoryItems("해열진통제", 1L, 1);
        verify(client).generateFunctionAnswer(
                question,
                modelContent,
                "get_inventory_items",
                result
        );
    }

    @Test
    void expiryQuestionExecutesExpiryTool() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        AiInventoryTool inventoryTool = mock(AiInventoryTool.class);
        AiExpiryTool expiryTool = mock(AiExpiryTool.class);

        String question = "30일 안에 유통기한 끝나는 재고 알려줘";

        JsonNode modelContent = mapper.readTree("""
                {
                  "role": "model",
                  "parts": [
                    {
                      "functionCall": {
                        "name": "get_expiring_inventory_items",
                        "args": {"days": 30, "expiry_status": "EXPIRING_SOON"}
                      }
                    }
                  ]
                }
                """);

        AiExpiryTool.ExpiringResult result = new AiExpiryTool.ExpiringResult(
                List.of(), 0, 1, 0, false, 30, "EXPIRING_SOON"
        );

        when(client.requestFunctionDecision(question)).thenReturn(modelContent);
        when(expiryTool.getExpiringInventoryItems(30, "EXPIRING_SOON", null, 1))
                .thenReturn(result);
        when(client.generateFunctionAnswer(
                question,
                modelContent,
                "get_expiring_inventory_items",
                result
        )).thenReturn("30일 안에 유통기한이 끝나는 재고가 없습니다.");

        AiChatResponse response = new AiChatService(client, inventoryTool, expiryTool)
                .chat(new AiChatRequest(question));

        assertEquals("EXPIRY", response.intent());
        assertEquals("get_expiring_inventory_items", response.sources().get(0).type());
        assertEquals("get_expiring_inventory_items", response.queryContext().functionName());
        assertEquals(30, response.queryContext().days());
        assertEquals("EXPIRING_SOON", response.queryContext().expiryStatus());
        assertEquals(0L, response.queryContext().totalCount());
        verifyNoInteractions(inventoryTool);
    }

    @Test
    void expiryForbiddenIsNotSwallowed() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        AiExpiryTool expiryTool = mock(AiExpiryTool.class);

        String question = "유통기한 만료 재고 알려줘";

        JsonNode modelContent = mapper.readTree("""
                {
                  "role": "model",
                  "parts": [
                    {"functionCall": {"name": "get_expiring_inventory_items",
                                      "args": {"expiry_status": "EXPIRED"}}}
                  ]
                }
                """);

        when(client.requestFunctionDecision(question)).thenReturn(modelContent);
        when(expiryTool.getExpiringInventoryItems(null, "EXPIRED", null, 1))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new AiChatService(client, mock(AiInventoryTool.class), expiryTool)
                        .chat(new AiChatRequest(question))
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(client, never()).generateFunctionAnswer(any(), any(), any(), any());
    }

    @Test
    void historyIsPassedToFunctionDecision() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        List<com.pharmlink.backend.domain.ai.dto.AiChatHistory> history = List.of(
                new com.pharmlink.backend.domain.ai.dto.AiChatHistory("user", "ERP가 뭐야?"),
                new com.pharmlink.backend.domain.ai.dto.AiChatHistory("assistant", "제약 유통 ERP입니다.")
        );

        JsonNode modelContent = mapper.readTree("""
                {"role": "model", "parts": [{"text": "네, 이어서 설명드릴게요."}]}
                """);

        when(client.requestFunctionDecision("더 자세히", history)).thenReturn(modelContent);

        AiChatResponse response = new AiChatService(
                client,
                mock(AiInventoryTool.class),
                mock(AiExpiryTool.class)
        ).chat(new AiChatRequest("더 자세히", history));

        assertEquals("GENERAL", response.intent());
        verify(client).requestFunctionDecision("더 자세히", history);
        verify(client, never()).requestFunctionDecision("더 자세히");
    }

    @Test
    void multipleFunctionCallsReturnGuidance() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        AiInventoryTool inventoryTool = mock(AiInventoryTool.class);
        AiExpiryTool expiryTool = mock(AiExpiryTool.class);

        String question = "재고 부족 상품과 유통기한 임박 상품을 알려줘";

        JsonNode modelContent = mapper.readTree("""
                {
                  "role": "model",
                  "parts": [
                    {
                      "functionCall": {
                        "name": "get_low_stock_items",
                        "args": {}
                      }
                    },
                    {
                      "functionCall": {
                        "name": "get_expiring_inventory_items",
                        "args": {}
                      }
                    }
                  ]
                }
                """);

        when(client.requestFunctionDecision(question))
                .thenReturn(modelContent);

        AiChatResponse response = new AiChatService(
                client,
                inventoryTool,
                expiryTool
        ).chat(new AiChatRequest(question));

        assertEquals("GENERAL", response.intent());
        assertTrue(
                response.answer().contains("한 번에 한 가지씩")
        );
        assertTrue(response.sources().isEmpty());
        assertNull(response.queryContext());

        verifyNoInteractions(inventoryTool, expiryTool);
    }


    @Test
    void directLowStockQuestionBypassesGemini() {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        AiInventoryTool inventoryTool = mock(AiInventoryTool.class);
        AiExpiryTool expiryTool = mock(AiExpiryTool.class);

        String question = "발주가 시급한 약 순서대로 알려줄래?";

        AiInventoryTool.LowStockResult result =
                new AiInventoryTool.LowStockResult(
                        List.of(
                                new com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse(
                                        1L, 1L, 1L,
                                        "TEST-001",
                                        "테스트 해열진통제 650mg",
                                        7,
                                        20
                                )
                        ),
                        1,
                        1,
                        1,
                        false
                );

        when(inventoryTool.getLowStockItems(null, 1))
                .thenReturn(result);

        AiChatService service =
                new AiChatService(client, inventoryTool, expiryTool);

        AiChatResponse response =
                service.chat(new AiChatRequest(question));

        assertEquals("LOW_STOCK", response.intent());
        assertTrue(response.answer().contains("테스트 해열진통제 650mg"));
        assertTrue(response.answer().contains("7개"));
        assertTrue(response.answer().contains("20개"));
        assertEquals(1, response.sources().size());
        assertEquals(1L, response.queryContext().totalCount());

        verify(inventoryTool).getLowStockItems(null, 1);
        verifyNoInteractions(client, expiryTool);
    }

}
