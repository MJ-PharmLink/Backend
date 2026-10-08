package com.pharmlink.backend.domain.user.dto;

import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.entity.User;

// 사용자 목록, 사용자 수정 응답
public record UserResponse(
        Long userId,
        String username,
        String name,
        Role role,
        boolean isActive
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getUserId(), user.getUsername(), user.getName(), user.getRole(), user.isActive());
    }
}
