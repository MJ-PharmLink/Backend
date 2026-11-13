package com.pharmlink.backend.domain.dashboard.controller;

import com.pharmlink.backend.domain.dashboard.dto.DashboardSummaryResponse;
import com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse;
import com.pharmlink.backend.domain.dashboard.dto.RecentOrderResponse;
import com.pharmlink.backend.domain.dashboard.service.DashboardService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// 대시보드 API (12장) — 관리자·영업·창고
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // 12.1 매출/매입/마진 요약 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> getSummary(
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(dashboardService.getSummary(startDate, endDate));
    }

    // 12.2 재고 부족 품목 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/low-stock-items")
    public ApiResponse<List<LowStockItemResponse>> getLowStockItems(
            @RequestParam(name = "warehouse_id", required = false) Long warehouseId,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.success(dashboardService.getLowStockItems(warehouseId, page, pageSize));
    }

    // 12.3 최근 주문/납품 현황 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/recent-orders")
    public ApiResponse<List<RecentOrderResponse>> getRecentOrders(@RequestParam(required = false) Integer limit) {
        return ApiResponse.success(dashboardService.getRecentOrders(limit));
    }
}
