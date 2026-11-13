package com.pharmlink.backend.domain.purchase.controller;

import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateRequest;
import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateResponse;
import com.pharmlink.backend.domain.purchase.dto.PurchaseDetailResponse;
import com.pharmlink.backend.domain.purchase.dto.PurchaseResponse;
import com.pharmlink.backend.domain.purchase.service.PurchaseService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import com.pharmlink.backend.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// 매입 API (10장) — 관리자·창고 전용
@RestController
@RequestMapping("/api/v1/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    // 10.1 매입 목록 조회
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @GetMapping
    public ApiResponse<List<PurchaseResponse>> getPurchases(
            @RequestParam(name = "partner_id", required = false) Long partnerId,
            @RequestParam(name = "warehouse_id", required = false) Long warehouseId,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.success(
                purchaseService.getPurchases(partnerId, warehouseId, startDate, endDate, page, pageSize));
    }

    // 10.2 매입 등록 (입고·재고 증가) → 201 Created
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseCreateResponse>> createPurchase(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PurchaseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(purchaseService.createPurchase(principal.userId(), request)));
    }

    // 10.3 매입 상세 조회
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @GetMapping("/{purchaseId}")
    public ApiResponse<PurchaseDetailResponse> getPurchase(@PathVariable Long purchaseId) {
        return ApiResponse.success(purchaseService.getPurchase(purchaseId));
    }
}
