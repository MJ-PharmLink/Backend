package com.pharmlink.backend.domain.company.dto;

import com.pharmlink.backend.domain.company.entity.Company;

import java.time.Instant;

// 회사 정보 조회·수정 응답 (company_id ~ email + updated_at)
public record CompanyResponse(
        Long companyId,
        String name,
        String businessNumber,
        String representativeName,
        String wholesaleLicenseNumber,
        String address,
        String phone,
        String fax,
        String email,
        Instant updatedAt
) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(company.getCompanyId(), company.getName(), company.getBusinessNumber(),
                company.getRepresentativeName(), company.getWholesaleLicenseNumber(), company.getAddress(),
                company.getPhone(), company.getFax(), company.getEmail(), company.getUpdatedAt());
    }
}
