package com.pharmlink.backend.domain.inventory.controller;

import com.pharmlink.backend.domain.inventory.dto.InventoryAdjustmentRequest;
import com.pharmlink.backend.domain.inventory.dto.InventoryTransactionResponse;
import com.pharmlink.backend.domain.inventory.service.InventoryAdjustmentService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import com.pharmlink.backend.global.security.UserPrincipal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// API 명세서 7.4 — 재고 조정
@RestController
@RequestMapping("/api/v1/inventory-adjustments")
@RequiredArgsConstructor
public class InventoryAdjustmentController {

    private final InventoryAdjustmentService inventoryAdjustmentService;

    // 관리자 전용 재고 조정
    // POST /api/v1/inventory-adjustments
    @PreAuthorize(AccessRole.ADMIN)
    @PostMapping
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> adjustInventory(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        InventoryTransactionResponse response =
                inventoryAdjustmentService.adjust(principal.userId(), request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
