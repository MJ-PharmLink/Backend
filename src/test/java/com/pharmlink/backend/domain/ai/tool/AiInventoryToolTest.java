package com.pharmlink.backend.domain.ai.tool;

import com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse;
import com.pharmlink.backend.domain.dashboard.service.DashboardService;
import com.pharmlink.backend.domain.inventory.service.InventoryQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiInventoryToolTest {

    @BeforeEach
    void setUp() {
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
    void returnsLowStockItems() {
        DashboardService dashboardService = mock(DashboardService.class);
        AiInventoryTool tool = new AiInventoryTool(
                dashboardService,
                mock(InventoryQueryService.class)
        );

        Page<LowStockItemResponse> page = new PageImpl<>(
                List.of(),
                PageRequest.of(0, 10),
                0
        );

        when(dashboardService.getLowStockItems(1L, 1, 10))
                .thenReturn(page);

        AiInventoryTool.LowStockResult result =
                tool.getLowStockItems(1L, 1);

        assertEquals(0, result.totalCount());
        assertEquals(1, result.page());
        assertEquals(0, result.totalPages());
        assertFalse(result.hasNext());

        verify(dashboardService).getLowStockItems(1L, 1, 10);
    }

    @Test
    void limitsPageNumberToMaximum() {
        DashboardService dashboardService = mock(DashboardService.class);
        AiInventoryTool tool = new AiInventoryTool(
                dashboardService,
                mock(InventoryQueryService.class)
        );

        Page<LowStockItemResponse> page = new PageImpl<>(List.of());

        when(dashboardService.getLowStockItems(null, 100, 10))
                .thenReturn(page);

        tool.getLowStockItems(null, 999);

        verify(dashboardService).getLowStockItems(null, 100, 10);
    }

    @Test
    void defaultsPageNumberToOne() {
        DashboardService dashboardService = mock(DashboardService.class);
        AiInventoryTool tool = new AiInventoryTool(
                dashboardService,
                mock(InventoryQueryService.class)
        );

        Page<LowStockItemResponse> page = new PageImpl<>(List.of());

        when(dashboardService.getLowStockItems(null, 1, 10))
                .thenReturn(page);

        tool.getLowStockItems(null, null);

        verify(dashboardService).getLowStockItems(null, 1, 10);
    }

    @Test
    void unauthenticatedLowStockReturnsUnauthorizedNotForbidden() {
        SecurityContextHolder.clearContext();

        DashboardService dashboardService = mock(DashboardService.class);
        AiInventoryTool tool = new AiInventoryTool(
                dashboardService,
                mock(InventoryQueryService.class)
        );

        com.pharmlink.backend.global.exception.BusinessException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.pharmlink.backend.global.exception.BusinessException.class,
                        () -> tool.getLowStockItems(null, 1)
                );

        assertEquals(
                com.pharmlink.backend.global.exception.ErrorCode.UNAUTHORIZED,
                exception.getErrorCode()
        );
        org.mockito.Mockito.verifyNoInteractions(dashboardService);
    }
}
