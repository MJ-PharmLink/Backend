package com.pharmlink.backend.domain.auth.dto;

import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.entity.User;

import java.time.Instant;

// 내 정보 조회 응답
public record MeResponse(
        Long userId,
        String username,
        String name,
        Role role,
        Instant createdAt
) {

    public static MeResponse from(User user) {
        return new MeResponse(user.getUserId(), user.getUsername(), user.getName(), user.getRole(),
                user.getCreatedAt());
    }
}
