package com.pharmlink.backend.domain.partner.dto;

import com.pharmlink.backend.domain.partner.entity.PartnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 거래처 등록 요청 (partner_type 값 오류는 422, 나머지 형식 오류는 400)
public record PartnerCreateRequest(
        @NotNull PartnerType partnerType,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = PartnerUpdateRequest.BUSINESS_NUMBER_PATTERN,
                message = "사업자등록번호는 000-00-00000 형식이어야 합니다.") String businessNumber,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 50) String managerName
) {
}
