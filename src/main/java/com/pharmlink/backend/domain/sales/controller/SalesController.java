package com.pharmlink.backend.domain.sales.controller;

import com.pharmlink.backend.domain.sales.dto.SaleDetailResponse;
import com.pharmlink.backend.domain.sales.dto.SaleResponse;
import com.pharmlink.backend.domain.sales.service.SalesService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// 매출 API (11.1·11.2) — 관리자·영업 전용. 매출 등록·수정 API는 없다 (납품 완료 시 자동 생성)
@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesService salesService;

    // 11.1 매출 목록 조회
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @GetMapping
    public ApiResponse<List<SaleResponse>> getSales(
            @RequestParam(name = "partner_id", required = false) Long partnerId,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.success(salesService.getSales(partnerId, startDate, endDate, page, pageSize));
    }

    // 11.2 매출 상세 및 거래 건별 마진 조회
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @GetMapping("/{saleId}")
    public ApiResponse<SaleDetailResponse> getSale(@PathVariable Long saleId) {
        return ApiResponse.success(salesService.getSale(saleId));
    }
}
