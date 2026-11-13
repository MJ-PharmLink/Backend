package com.pharmlink.backend.domain.partner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 거래처 정보 수정 요청 (전체 교체). partner_type 필드가 없어서 보내도 무시되고 변경되지 않는다
public record PartnerUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = BUSINESS_NUMBER_PATTERN,
                message = "사업자등록번호는 000-00-00000 형식이어야 합니다.") String businessNumber,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 255) String address,
        @Size(max = 50) String managerName
) {
    public static final String BUSINESS_NUMBER_PATTERN = "^\\d{3}-\\d{2}-\\d{5}$";
}
