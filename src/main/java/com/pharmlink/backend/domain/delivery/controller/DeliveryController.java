package com.pharmlink.backend.domain.delivery.controller;

import com.pharmlink.backend.domain.delivery.dto.DeliveryCompleteResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryDetailResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryShipResponse;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.delivery.service.DeliveryService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// 납품 API (9장) — 조회는 전 역할, 출고·납품 완료는 관리자·창고
@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    // 9.1 납품 목록 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<List<DeliveryResponse>> getDeliveries(
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(name = "partner_id", required = false) Long partnerId,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.success(deliveryService.getDeliveries(status, partnerId, startDate, endDate, page, pageSize));
    }

    // 9.2 납품 상세 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/{deliveryId}")
    public ApiResponse<DeliveryDetailResponse> getDelivery(@PathVariable Long deliveryId) {
        return ApiResponse.success(deliveryService.getDelivery(deliveryId));
    }

    // 9.3 출고 완료 처리 (WAITING → SHIPPED)
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @PostMapping("/{deliveryId}/ship")
    public ApiResponse<DeliveryShipResponse> ship(@PathVariable Long deliveryId) {
        return ApiResponse.success(deliveryService.ship(deliveryId));
    }

    // 9.4 납품 완료 처리 (SHIPPED → DELIVERED, 매출 자동 생성)
    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @PostMapping("/{deliveryId}/complete")
    public ApiResponse<DeliveryCompleteResponse> complete(@PathVariable Long deliveryId) {
        return ApiResponse.success(deliveryService.complete(deliveryId));
    }
}
