package com.pharmlink.backend.domain.user.dto;

import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.entity.User;

import java.time.Instant;

// 사용자 계정 생성 응답 (created_at 포함)
public record UserCreateResponse(
        Long userId,
        String username,
        String name,
        Role role,
        boolean isActive,
        Instant createdAt
) {
    public static UserCreateResponse from(User user) {
        return new UserCreateResponse(user.getUserId(), user.getUsername(), user.getName(), user.getRole(),
                user.isActive(), user.getCreatedAt());
    }
}
