package com.pharmlink.backend.domain.ai.client;

import com.pharmlink.backend.domain.ai.dto.AiChatHistory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiHistoryContentsTest {

    @Test
    void mapsAssistantToModelAndAppendsCurrentQuestion() {
        List<Map<String, Object>> contents = GeminiLlmClient.toContents(
                List.of(
                        new AiChatHistory("user", "해열진통제 재고 알려줘"),
                        new AiChatHistory("assistant", "본사 창고에 7개 있습니다.")
                ),
                "그럼 1번 창고는?"
        );

        assertEquals(3, contents.size());
        assertEquals("user", contents.get(0).get("role"));
        assertEquals("model", contents.get(1).get("role"));
        assertEquals("user", contents.get(2).get("role"));
        assertEquals(
                List.of(Map.of("text", "그럼 1번 창고는?")),
                contents.get(2).get("parts")
        );
    }

    @Test
    void noHistoryKeepsSingleUserMessage() {
        assertEquals(1, GeminiLlmClient.toContents(List.of(), "안녕").size());
        assertEquals(1, GeminiLlmClient.toContents(null, "안녕").size());
    }
}
