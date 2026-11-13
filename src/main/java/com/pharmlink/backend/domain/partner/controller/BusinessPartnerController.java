package com.pharmlink.backend.domain.partner.controller;

import com.pharmlink.backend.domain.partner.dto.PartnerCreateRequest;
import com.pharmlink.backend.domain.partner.dto.PartnerDetailResponse;
import com.pharmlink.backend.domain.partner.dto.PartnerResponse;
import com.pharmlink.backend.domain.partner.dto.PartnerTransactionResponse;
import com.pharmlink.backend.domain.partner.dto.PartnerTransactionType;
import com.pharmlink.backend.domain.partner.dto.PartnerUpdateRequest;
import com.pharmlink.backend.domain.partner.entity.PartnerType;
import com.pharmlink.backend.domain.partner.service.PartnerService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

// 거래처 관리 API — 목록·상세 조회는 관리자·영업·창고, 나머지는 관리자·영업
@RestController
@RequestMapping("/api/v1/business-partners")
@RequiredArgsConstructor
public class BusinessPartnerController {

    private final PartnerService partnerService;

    // 거래처 목록 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<List<PartnerResponse>> getPartners(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "partner_type", required = false) PartnerType partnerType,
            @RequestParam(required = false) String keyword,
            @RequestParam(name = "include_inactive", defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(partnerService.getPartners(page, pageSize, partnerType, keyword, includeInactive));
    }

    // 거래처 등록 → 201 Created
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @PostMapping
    public ResponseEntity<ApiResponse<PartnerDetailResponse>> createPartner(
            @Valid @RequestBody PartnerCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(partnerService.createPartner(request)));
    }

    // 거래처 상세 조회 (비활성 거래처 포함)
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/{partnerId}")
    public ApiResponse<PartnerDetailResponse> getPartner(@PathVariable Long partnerId) {
        return ApiResponse.success(partnerService.getPartner(partnerId));
    }

    // 거래처 정보 수정 (전체 교체)
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @PutMapping("/{partnerId}")
    public ApiResponse<PartnerDetailResponse> updatePartner(@PathVariable Long partnerId,
                                                            @Valid @RequestBody PartnerUpdateRequest request) {
        return ApiResponse.success(partnerService.updatePartner(partnerId, request));
    }

    // 거래처 삭제(비활성화) → 204 No Content (이미 비활성이어도 204)
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @DeleteMapping("/{partnerId}")
    public ResponseEntity<Void> deactivatePartner(@PathVariable Long partnerId) {
        partnerService.deactivatePartner(partnerId);
        return ResponseEntity.noContent().build();
    }

    // 거래처별 거래 이력 조회 (최신순)
    @PreAuthorize(AccessRole.ADMIN_SALES)
    @GetMapping("/{partnerId}/transactions")
    public ApiResponse<List<PartnerTransactionResponse>> getTransactions(
            @PathVariable Long partnerId,
            @RequestParam(required = false) PartnerTransactionType type,
            @RequestParam(name = "start_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "end_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize) {
        return ApiResponse.success(partnerService.getTransactions(partnerId, type, startDate, endDate, page, pageSize));
    }
}
