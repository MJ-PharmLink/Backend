package com.pharmlink.backend.domain.partner.dto;

import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;

// 거래처 목록 응답 항목
public record PartnerResponse(
        Long partnerId,
        PartnerType partnerType,
        String name,
        String businessNumber,
        String phone,
        String address,
        String managerName,
        boolean isActive
) {
    public static PartnerResponse from(BusinessPartner partner) {
        return new PartnerResponse(partner.getPartnerId(), partner.getPartnerType(), partner.getName(),
                partner.getBusinessNumber(), partner.getPhone(), partner.getAddress(), partner.getManagerName(),
                partner.isActive());
    }
}
