package com.pharmlink.backend.domain.order.controller;

import com.pharmlink.backend.domain.order.dto.OrderApproveResponse;
import com.pharmlink.backend.domain.order.dto.OrderCancelRequest;
import com.pharmlink.backend.domain.order.dto.OrderCancelResponse;
import com.pharmlink.backend.domain.order.dto.OrderCreateRequest;
import com.pharmlink.backend.domain.order.dto.OrderCreateResponse;
import com.pharmlink.backend.domain.order.dto.OrderDetailResponse;
import com.pharmlink.backend.domain.order.dto.OrderResponse;
import com.pharmlink.backend.domain.order.entity.OrderStatus;
import com.pharmlink.backend.domain.order.service.OrderService;
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

// 주문 API (8장) — 관리자·영업 전용
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 8.1 주문 목록 조회
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @GetMapping
    public ApiResponse<List<OrderResponse>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(name = "partner_id", required = false) Long partnerId,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.success(orderService.getOrders(status, partnerId, startDate, endDate, page, pageSize));
    }

    // 8.2 주문 등록 → 201 Created
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @PostMapping
    public ResponseEntity<ApiResponse<OrderCreateResponse>> createOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(orderService.createOrder(principal.userId(), request)));
    }

    // 8.3 주문 상세 조회
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @GetMapping("/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.getOrder(orderId));
    }

    // 8.4 주문 승인 (재고 차감·납품 생성)
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @PostMapping("/{orderId}/approve")
    public ApiResponse<OrderApproveResponse> approveOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long orderId) {
        return ApiResponse.success(orderService.approveOrder(orderId, principal.userId()));
    }

    // 8.5 주문 취소 (본문 생략 가능)
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderCancelResponse> cancelOrder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) OrderCancelRequest request) {
        return ApiResponse.success(orderService.cancelOrder(orderId, principal.userId(), request));
    }
}
