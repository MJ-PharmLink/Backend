package com.pharmlink.backend.domain.company.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

// 회사 정보 수정 요청 (전체 교체). 선택 필드(wholesale_license_number, fax, email)는 보내지 않거나 비우면 null로 바뀐다
public record CompanyUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$",
                message = "사업자등록번호는 000-00-00000 형식이어야 합니다.") String businessNumber,
        @NotBlank @Size(max = 50) String representativeName,
        @Size(max = 50) String wholesaleLicenseNumber,
        @NotBlank @Size(max = 255) String address,
        @NotBlank @Size(max = 20) String phone,
        @Size(max = 20) String fax,
        @Email @Size(max = 100) String email
) {
    // 선택 필드는 공백만 보내도 null로 바꿔서 검증한다 (email = "  "이 형식 오류가 되지 않도록)
    public CompanyUpdateRequest {
        wholesaleLicenseNumber = blankToNull(wholesaleLicenseNumber);
        fax = blankToNull(fax);
        email = blankToNull(email);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
