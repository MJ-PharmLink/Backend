package com.pharmlink.backend.domain.ai.client;

import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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

    @Test
    void rateLimitReturnsTooManyRequests() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> callFakeGemini(429, """
                        {"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}
                        """)
        );

        assertEquals(ErrorCode.AI_RATE_LIMITED, exception.getErrorCode());
        assertEquals(
                HttpStatus.TOO_MANY_REQUESTS,
                exception.getErrorCode().getStatus()
        );
    }

    @Test
    void upstreamDeadlineReturnsGatewayTimeout() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> callFakeGemini(504, """
                        {"error":{"code":504,"status":"DEADLINE_EXCEEDED"}}
                        """)
        );

        assertEquals(ErrorCode.AI_TIMEOUT, exception.getErrorCode());
        assertEquals(
                HttpStatus.GATEWAY_TIMEOUT,
                exception.getErrorCode().getStatus()
        );
    }

    @Test
    void invalidKeyStillReturnsServiceUnavailable() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> callFakeGemini(400, """
                        {"error":{"code":400,"status":"INVALID_ARGUMENT"}}
                        """)
        );

        assertEquals(
                ErrorCode.AI_SERVICE_UNAVAILABLE,
                exception.getErrorCode()
        );
    }

    @Test
    void readTimeoutReturnsGatewayTimeout() throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0),
                0
        );

        server.createContext("/", exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });

        server.start();

        try {
            GeminiLlmClient client = new GeminiLlmClient(
                    "test-api-key",
                    "test-model",
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofMillis(300)
            );

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> client.generateAnswer("재고 현황 알려줘")
            );

            assertEquals(ErrorCode.AI_TIMEOUT, exception.getErrorCode());
        } finally {
            server.stop(0);
        }
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
