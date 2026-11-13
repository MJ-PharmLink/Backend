package com.pharmlink.backend.domain.company.controller;

import com.pharmlink.backend.domain.company.dto.CompanyResponse;
import com.pharmlink.backend.domain.company.dto.CompanyUpdateRequest;
import com.pharmlink.backend.domain.company.service.CompanyService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 회사 정보 API — 조회는 로그인한 모든 역할, 수정은 관리자 전용
@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    // 회사 정보 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<CompanyResponse> getCompany() {
        return ApiResponse.success(companyService.getCompany());
    }

    // 회사 정보 수정 (전체 교체)
    @PreAuthorize(AccessRole.ADMIN)
    @PutMapping
    public ApiResponse<CompanyResponse> updateCompany(@Valid @RequestBody CompanyUpdateRequest request) {
        return ApiResponse.success(companyService.updateCompany(request));
    }
}
