package com.pharmlink.backend.domain.inventory.controller;

import com.pharmlink.backend.domain.inventory.dto.InventoryLotResponse;
import com.pharmlink.backend.domain.inventory.dto.InventoryResponse;
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

    // 1. 재고 목록 조회
    // GET /api/v1/inventories?page=1&page_size=20
    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<List<InventoryResponse>> getInventories(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize
    ) {
        Page<InventoryResponse> inventories =
                inventoryQueryService.getInventories(page, pageSize);

        return ApiResponse.success(inventories);
    }

    // 2. 재고 상세 조회
    // GET /api/v1/inventories/{inventoryId}
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/{inventoryId}")
    public ApiResponse<InventoryResponse> getInventory(
            @PathVariable Long inventoryId
    ) {
        return ApiResponse.success(
                inventoryQueryService.getInventory(inventoryId)
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
