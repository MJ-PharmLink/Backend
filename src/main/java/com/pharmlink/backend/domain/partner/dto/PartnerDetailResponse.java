package com.pharmlink.backend.domain.partner.dto;

import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;

import java.time.Instant;

// 거래처 등록·상세·수정 응답 (목록 항목 + created_at, updated_at)
public record PartnerDetailResponse(
        Long partnerId,
        PartnerType partnerType,
        String name,
        String businessNumber,
        String phone,
        String address,
        String managerName,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public static PartnerDetailResponse from(BusinessPartner partner) {
        return new PartnerDetailResponse(partner.getPartnerId(), partner.getPartnerType(), partner.getName(),
                partner.getBusinessNumber(), partner.getPhone(), partner.getAddress(), partner.getManagerName(),
                partner.isActive(), partner.getCreatedAt(), partner.getUpdatedAt());
    }
}
