package com.pharmlink.backend.domain.ai.dto;

import com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// application.yaml 의 spring.jackson.property-naming-strategy = SNAKE_CASE 와 같은 조건으로 직렬화한다.
class AiChatResponseJsonTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    @Test
    void generalAnswerHasEmptySourcesAndNullQueryContext() {
        JsonNode json = mapper.valueToTree(
                new AiChatResponse(
                        "안녕하세요",
                        "GENERAL",
                        List.of(),
                        null,
                        Instant.parse("2026-11-24T01:00:00Z")
                )
        );

        assertTrue(json.path("sources").isArray());
        assertTrue(json.path("sources").isEmpty());
        assertTrue(json.has("query_context"));
        assertTrue(json.path("query_context").isNull());
        assertEquals("2026-11-24T01:00:00Z", json.path("generated_at").asText());
    }

    @Test
    void queryAnswerUsesSnakeCaseFields() {
        LowStockItemResponse row = new LowStockItemResponse(
                10L, 1L, 3L, "A001", "테스트 해열진통제 650mg", 7, 20
        );

        JsonNode json = mapper.valueToTree(
                AiChatResponse.ofQuery(
                        "부족 재고는 1건입니다.",
                        "LOW_STOCK",
                        new AiSource("get_low_stock_items", "부족 재고 (전체 창고)", List.of(row)),
                        AiQueryContext.of("get_low_stock_items", null, null, 1, 1, 1, false)
                )
        );

        JsonNode context = json.path("query_context");
        assertEquals("get_low_stock_items", context.path("function_name").asText());
        assertTrue(context.path("keyword").isNull());
        assertTrue(context.path("warehouse_id").isNull());
        assertEquals(1, context.path("total_count").asInt());
        assertEquals(1, context.path("total_pages").asInt());
        assertFalse(context.path("has_next").asBoolean());
        assertFalse(context.has("days"));
        assertFalse(context.has("expiry_status"));

        JsonNode firstRow = json.path("sources").path(0).path("rows").path(0);
        assertEquals("A001", firstRow.path("item_code").asText());
        assertEquals(20, firstRow.path("safety_stock").asInt());
        assertEquals(13, firstRow.path("shortage_quantity").asInt());
        assertEquals("LOW_STOCK", firstRow.path("stock_status").asText());
        assertTrue(json.path("generated_at").isTextual());
    }
}
