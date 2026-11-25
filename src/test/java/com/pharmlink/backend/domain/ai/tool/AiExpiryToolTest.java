package com.pharmlink.backend.domain.ai.tool;

import com.pharmlink.backend.domain.inventory.dto.ExpiringInventoryResponse;
import com.pharmlink.backend.domain.inventory.service.InventoryExpiringQueryService;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiExpiryToolTest {

    private final InventoryExpiringQueryService service =
            mock(InventoryExpiringQueryService.class);
    private final AiExpiryTool tool = new AiExpiryTool(service);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "WAREHOUSE"})
    void allowedRolesUseExistingExpiringService(String role) {
        loginAs(role);

        ExpiringInventoryResponse row = new ExpiringInventoryResponse(
                1L, 2L, 1L, 3L, "A001", "테스트 해열진통제 650mg", "LOT-1",
                LocalDate.of(2026, 12, 10), 16, 7, "EXPIRING_SOON"
        );
        when(service.getExpiringInventories(90, null, null, 1, 10))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 10), 1));

        AiExpiryTool.ExpiringResult result =
                tool.getExpiringInventoryItems(null, null, null, null);

        assertEquals(1, result.items().size());
        assertEquals(1L, result.totalCount());
        assertEquals(1, result.page());
        assertEquals(90, result.days());
        verify(service).getExpiringInventories(90, null, null, 1, 10);
    }

    @Test
    void salesIsForbiddenLikeExistingApi() {
        loginAs("SALES");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tool.getExpiringInventoryItems(30, null, null, 1)
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedIsUnauthorized() {
        SecurityContextHolder.clearContext();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tool.getExpiringInventoryItems(30, null, null, 1)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidArguments() {
        loginAs("ADMIN");

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> tool.getExpiringInventoryItems(366, null, null, 1)).getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> tool.getExpiringInventoryItems(30, "SOON", null, 1)).getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> tool.getExpiringInventoryItems(30, null, 0L, 1)).getErrorCode());

        verifyNoInteractions(service);
    }

    @Test
    void clampsPage() {
        loginAs("WAREHOUSE");
        when(service.getExpiringInventories(30, "EXPIRED", 1L, 100, 10))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(99, 10), 0));

        AiExpiryTool.ExpiringResult result =
                tool.getExpiringInventoryItems(30, "EXPIRED", 1L, 500);

        assertEquals(100, result.page());
        assertEquals(0L, result.totalCount());
    }

    private void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user",
                        null,
                        List.of(() -> "ROLE_" + role)
                )
        );
    }
}
