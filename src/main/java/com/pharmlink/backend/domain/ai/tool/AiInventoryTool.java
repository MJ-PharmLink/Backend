package com.pharmlink.backend.domain.ai.tool;

import com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse;
import com.pharmlink.backend.domain.dashboard.service.DashboardService;
import com.pharmlink.backend.domain.inventory.dto.InventoryResponse;
import com.pharmlink.backend.domain.inventory.service.InventoryQueryService;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiInventoryTool {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_PAGE = 100;

    private final DashboardService dashboardService;
    private final InventoryQueryService inventoryQueryService;

    public LowStockResult getLowStockItems(Long warehouseId, Integer page) {
        AiAuthorization.requireAuthenticated();

        int safePage = page == null ? 1 : Math.max(1, Math.min(page, MAX_PAGE));

        Page<LowStockItemResponse> result =
                dashboardService.getLowStockItems(warehouseId, safePage, PAGE_SIZE);

        return new LowStockResult(
                result.getContent(),
                result.getTotalElements(),
                safePage,
                result.getTotalPages(),
                result.hasNext()
        );
    }


    public InventorySearchResult getInventoryItems(
            String keyword,
            Long warehouseId,
            Integer page
    ) {
        AiAuthorization.requireAuthenticated();

        if (warehouseId != null && warehouseId < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        String safeKeyword =
                keyword == null || keyword.isBlank() ? null : keyword.trim();

        if (safeKeyword != null && safeKeyword.length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        int safePage =
                page == null ? 1 : Math.max(1, Math.min(page, MAX_PAGE));

        Page<InventoryResponse> result =
                inventoryQueryService.getInventories(
                        safeKeyword,
                        warehouseId,
                        null,
                        null,
                        null,
                        null,
                        safePage,
                        PAGE_SIZE
                );

        return new InventorySearchResult(
                result.getContent(),
                result.getTotalElements(),
                safePage,
                result.getTotalPages(),
                result.hasNext()
        );
    }

    public record InventorySearchResult(
            List<InventoryResponse> items,
            long totalCount,
            int page,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record LowStockResult(
            List<LowStockItemResponse> items,
            long totalCount,
            int page,
            int totalPages,
            boolean hasNext
    ) {
    }
}
