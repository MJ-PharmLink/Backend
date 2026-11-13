package com.pharmlink.backend.domain.user.dto;

import com.pharmlink.backend.domain.user.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 사용자 계정 생성 요청 (role 값 오류는 422, 나머지 형식 오류는 400)
public record UserCreateRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank @Size(max = 50) String name,
        @NotNull Role role
) {
}
