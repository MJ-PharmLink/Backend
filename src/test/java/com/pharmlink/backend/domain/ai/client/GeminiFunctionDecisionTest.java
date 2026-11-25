package com.pharmlink.backend.domain.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pharmlink.backend.domain.ai.service.AiChatService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GeminiFunctionDecisionTest {

    @Test
    void detectsLowStockFunctionCall() throws Exception {
        GeminiLlmClient client = mock(GeminiLlmClient.class);
        ObjectMapper mapper = new ObjectMapper();

        JsonNode response = mapper.readTree("""
                {
                  "role": "model",
                  "parts": [
                    {
                      "functionCall": {
                        "name": "get_low_stock_items",
                        "args": {
                          "page": 1
                        }
                      }
                    }
                  ]
                }
                """);

        when(client.requestFunctionDecision("재고 부족 상품 알려줘"))
                .thenReturn(response);

        JsonNode result =
                client.requestFunctionDecision("재고 부족 상품 알려줘");

        JsonNode functionCall = result.path("parts")
                .path(0)
                .path("functionCall");

        assertTrue(functionCall.isObject());
        assertEquals(
                "get_low_stock_items",
                functionCall.path("name").asText()
        );
        assertEquals(
                1,
                functionCall.path("args").path("page").asInt()
        );
    }
}
