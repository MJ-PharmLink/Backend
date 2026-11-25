package com.pharmlink.backend.domain.ai.tool;

import com.pharmlink.backend.domain.inventory.dto.ExpiringInventoryResponse;
import com.pharmlink.backend.domain.inventory.service.InventoryExpiringQueryService;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

// 유통기한 만료·임박 LOT 조회 : get_expiring_inventory_items, intent EXPIRY
// 기존 GET /api/v1/inventories/expiring 과 같은 서비스·응답 DTO·권한(ADMIN, WAREHOUSE)을 쓴다.
@Component
@RequiredArgsConstructor
public class AiExpiryTool {

    static final int DEFAULT_DAYS = 90;
    public static final int MAX_DAYS = 365;

    private static final int PAGE_SIZE = 10;
    private static final int MAX_PAGE = 100;
    private static final Set<String> ALLOWED_STATUSES =
            Set.of("EXPIRED", "EXPIRING_SOON");

    private final InventoryExpiringQueryService inventoryExpiringQueryService;

    public ExpiringResult getExpiringInventoryItems(
            Integer days,
            String expiryStatus,
            Long warehouseId,
            Integer page
    ) {
        // 기존 API 권한 : @PreAuthorize(AccessRole.ADMIN_WAREHOUSE). AI로 우회하지 않도록 동일하게 확인한다.
        AiAuthorization.requireAnyRole("ADMIN", "WAREHOUSE");

        int safeDays = days == null ? DEFAULT_DAYS : days;

        if (safeDays < 0 || safeDays > MAX_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (expiryStatus != null && !ALLOWED_STATUSES.contains(expiryStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (warehouseId != null && warehouseId < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        int safePage = page == null ? 1 : Math.max(1, Math.min(page, MAX_PAGE));

        Page<ExpiringInventoryResponse> result =
                inventoryExpiringQueryService.getExpiringInventories(
                        safeDays,
                        expiryStatus,
                        warehouseId,
                        safePage,
                        PAGE_SIZE
                );

        return new ExpiringResult(
                result.getContent(),
                result.getTotalElements(),
                safePage,
                result.getTotalPages(),
                result.hasNext(),
                safeDays,
                expiryStatus
        );
    }

    public record ExpiringResult(
            List<ExpiringInventoryResponse> items,
            long totalCount,
            int page,
            int totalPages,
            boolean hasNext,
            int days,
            String expiryStatus
    ) {
    }
}
