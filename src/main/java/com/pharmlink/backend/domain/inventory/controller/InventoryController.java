package com.pharmlink.backend.domain.inventory.controller;

import com.pharmlink.backend.domain.inventory.dto.InventoryLotResponse;
import com.pharmlink.backend.domain.inventory.dto.InventoryResponse;
import com.pharmlink.backend.domain.inventory.dto.InventoryDetailResponse;
import com.pharmlink.backend.domain.inventory.dto.ExpiringInventoryResponse;
import com.pharmlink.backend.domain.inventory.service.InventoryExpiringQueryService;
import com.pharmlink.backend.domain.inventory.service.InventoryQueryService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventories")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryQueryService inventoryQueryService;
    private final InventoryExpiringQueryService inventoryExpiringQueryService;

    // 1. 재고 목록 조회
    // GET /api/v1/inventories?page=1&page_size=20
    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<List<InventoryResponse>> getInventories(
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "warehouse_id", required = false)
            Long warehouseId,
            @RequestParam(name = "category_id", required = false)
            Long categoryId,
            @RequestParam(name = "stock_status", required = false)
            String stockStatus,
            @RequestParam(name = "item_status", required = false)
            String itemStatus,
            @RequestParam(name = "expiry_status", required = false)
            String expiryStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false)
            Integer pageSize
    ) {
        Page<InventoryResponse> inventories =
                inventoryQueryService.getInventories(
                        keyword,
                        warehouseId,
                        categoryId,
                        stockStatus,
                        itemStatus,
                        expiryStatus,
                        page,
                        pageSize
                );

        return ApiResponse.success(inventories);
    }


    // 7.3 유통기한 만료 및 임박 LOT 조회
    // GET /api/v1/inventories/expiring
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @GetMapping("/expiring")
    public ApiResponse<List<ExpiringInventoryResponse>> getExpiringInventories(
            @RequestParam(required = false) Integer days,
            @RequestParam(name = "expiry_status", required = false)
            String expiryStatus,
            @RequestParam(name = "warehouse_id", required = false)
            Long warehouseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false)
            Integer pageSize
    ) {
        Page<ExpiringInventoryResponse> results =
                inventoryExpiringQueryService.getExpiringInventories(
                        days,
                        expiryStatus,
                        warehouseId,
                        page,
                        pageSize
                );

        return ApiResponse.success(results);
    }

    // 2. 재고 상세 조회
    // GET /api/v1/inventories/{inventoryId}
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/{inventoryId}")
    public ApiResponse<InventoryDetailResponse> getInventory(
            @PathVariable Long inventoryId
    ) {
        return ApiResponse.success(
                inventoryQueryService.getInventoryDetail(inventoryId)
        );
    }

    // 3. 해당 재고의 LOT 목록 조회
    // GET /api/v1/inventories/{inventoryId}/lots
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/{inventoryId}/lots")
    public ApiResponse<List<InventoryLotResponse>> getInventoryLots(
            @PathVariable Long inventoryId
    ) {
        return ApiResponse.success(
                inventoryQueryService.getInventoryLots(inventoryId)
        );
    }
}
