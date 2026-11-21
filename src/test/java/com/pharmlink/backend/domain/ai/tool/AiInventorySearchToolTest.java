package com.pharmlink.backend.domain.ai.tool;

import com.pharmlink.backend.domain.dashboard.service.DashboardService;
import com.pharmlink.backend.domain.inventory.dto.InventoryResponse;
import com.pharmlink.backend.domain.inventory.service.InventoryQueryService;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiInventorySearchToolTest {

    private InventoryQueryService inventoryQueryService;
    private AiInventoryTool tool;

    @BeforeEach
    void setUp() {
        inventoryQueryService = mock(InventoryQueryService.class);

        tool = new AiInventoryTool(
                mock(DashboardService.class),
                inventoryQueryService
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user",
                        null,
                        List.of(() -> "ROLE_ADMIN")
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void searchesByKeywordAndWarehouse() {
        Page<InventoryResponse> page = new PageImpl<>(List.of());

        when(inventoryQueryService.getInventories(
                "해열진통제", 1L, null, null, null, null, 1, 10
        )).thenReturn(page);

        AiInventoryTool.InventorySearchResult result =
                tool.getInventoryItems("  해열진통제  ", 1L, 1);

        assertEquals(0, result.totalCount());
        assertEquals(1, result.page());
        assertFalse(result.hasNext());

        verify(inventoryQueryService).getInventories(
                "해열진통제", 1L, null, null, null, null, 1, 10
        );
    }

    @Test
    void searchesByWarehouseOnly() {
        Page<InventoryResponse> page = new PageImpl<>(List.of());

        when(inventoryQueryService.getInventories(
                null, 2L, null, null, null, null, 1, 10
        )).thenReturn(page);

        tool.getInventoryItems(null, 2L, null);

        verify(inventoryQueryService).getInventories(
                null, 2L, null, null, null, null, 1, 10
        );
    }

    @Test
    void limitsPageNumberToOneHundred() {
        Page<InventoryResponse> page = new PageImpl<>(List.of());

        when(inventoryQueryService.getInventories(
                null, null, null, null, null, null, 100, 10
        )).thenReturn(page);

        tool.getInventoryItems(null, null, 999);

        verify(inventoryQueryService).getInventories(
                null, null, null, null, null, null, 100, 10
        );
    }

    @Test
    void rejectsInvalidWarehouseId() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tool.getInventoryItems(null, -1L, 1)
        );

        assertEquals(
                ErrorCode.VALIDATION_ERROR,
                exception.getErrorCode()
        );

        verifyNoInteractions(inventoryQueryService);
    }

    @Test
    void rejectsLongKeyword() {
        String keyword = "A".repeat(101);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tool.getInventoryItems(keyword, null, 1)
        );

        assertEquals(
                ErrorCode.VALIDATION_ERROR,
                exception.getErrorCode()
        );

        verifyNoInteractions(inventoryQueryService);
    }

    @Test
    void rejectsUnauthenticatedUser() {
        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> tool.getInventoryItems("해열진통제", null, 1)
        );

        verifyNoInteractions(inventoryQueryService);
    }
}
