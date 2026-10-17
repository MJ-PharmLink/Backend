package com.pharmlink.backend.domain.inventory.controller;

import com.pharmlink.backend.domain.inventory.dto.InventoryTransactionResponse;
import com.pharmlink.backend.domain.inventory.entity.TransactionType;
import com.pharmlink.backend.domain.inventory.service.InventoryTransactionQueryService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory-transactions")
@RequiredArgsConstructor
public class InventoryTransactionController {

    private final InventoryTransactionQueryService inventoryTransactionQueryService;

    // 재고 입출고 이력 목록 조회
    // 상품 ID, 창고 ID, 입출고 유형으로 필터링 가능
    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<List<InventoryTransactionResponse>> getTransactions(
            @RequestParam(name = "item_id", required = false) Long itemId,
            @RequestParam(name = "warehouse_id", required = false) Long warehouseId,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize
    ) {
        Page<InventoryTransactionResponse> transactions =
                inventoryTransactionQueryService.getTransactions(
                        itemId,
                        warehouseId,
                        type,
                        page,
                        pageSize
                );

        return ApiResponse.success(transactions);
    }
}
