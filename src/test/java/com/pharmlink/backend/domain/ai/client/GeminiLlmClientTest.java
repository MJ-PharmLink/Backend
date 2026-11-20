package com.pharmlink.backend.domain.ai.client;

import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeminiLlmClientTest {

    @Test
    void apiFailureReturnsServiceUnavailable() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> callFakeGemini(503, """
                        {"error":{"code":503,"message":"Unavailable"}}
                        """)
        );

        assertEquals(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getErrorCode().getStatus()
        );
    }

    @Test
    void emptyAnswerReturnsServiceUnavailable() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> callFakeGemini(200, """
                        {"candidates":[{"content":{"parts":[]}}]}
                        """)
        );

        assertEquals(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );
    }

    private void callFakeGemini(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/", exchange -> {
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json"
            );
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });

        server.start();

        try {
            GeminiLlmClient client = new GeminiLlmClient(
                    "test-api-key",
                    "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            client.generateAnswer("재고 현황 알려줘");
        } finally {
            server.stop(0);
        }
    }
}
