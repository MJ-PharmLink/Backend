package com.pharmlink.backend.domain.user.dto;

import com.pharmlink.backend.domain.user.entity.Role;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 사용자 정보/역할 수정 요청: 보낸 필드만 수정 (null이면 변경 안 함)
public record UserUpdateRequest(
        @Size(max = 50) @Pattern(regexp = ".*\\S.*", message = "공백만 입력할 수 없습니다.") String name,
        Role role,
        Boolean isActive,
        @Size(min = 8, max = 64) String password
) {
}
