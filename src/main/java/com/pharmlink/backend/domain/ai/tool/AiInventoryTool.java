package com.pharmlink.backend.domain.ai.tool;

import com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse;
import com.pharmlink.backend.domain.dashboard.service.DashboardService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiInventoryTool {

    private static final int PAGE_SIZE = 10;
    private static final int MAX_PAGE = 100;

    private final DashboardService dashboardService;

    @PreAuthorize("isAuthenticated()")
    public LowStockResult getLowStockItems(Long warehouseId, Integer page) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "인증이 필요합니다."
            );
        }

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

    public record LowStockResult(
            List<LowStockItemResponse> items,
            long totalCount,
            int page,
            int totalPages,
            boolean hasNext
    ) {
    }
}
