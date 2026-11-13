package com.pharmlink.backend.domain.sales.controller;

import com.pharmlink.backend.domain.sales.dto.MarginGroupBy;
import com.pharmlink.backend.domain.sales.dto.MarginSummaryResponse;
import com.pharmlink.backend.domain.sales.service.SalesService;
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

// 마진 집계 API (11.3) — 관리자·영업 전용
@RestController
@RequestMapping("/api/v1/margins")
@RequiredArgsConstructor
public class MarginController {

    private final SalesService salesService;

    // 11.3 마진 집계 조회. start_date·end_date 누락은 400 VALIDATION_ERROR, group_by 값 오류는 422 UNPROCESSABLE_ENTITY
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @GetMapping("/summary")
    public ApiResponse<MarginSummaryResponse> getMarginSummary(
            @RequestParam(name = "start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "group_by", required = false) MarginGroupBy groupBy,
            @RequestParam(name = "partner_id", required = false) Long partnerId,
            @RequestParam(name = "item_id", required = false) Long itemId) {
        return ApiResponse.success(salesService.getMarginSummary(startDate, endDate, groupBy, partnerId, itemId));
    }
}
