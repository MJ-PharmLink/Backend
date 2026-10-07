package com.pharmlink.backend.domain.auth.dto;

import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.entity.User;

// 로그인 응답
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        LoginUser user
) {

    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
        return new LoginResponse(accessToken, refreshToken, TokenResponse.TOKEN_TYPE, expiresIn,
                new LoginUser(user.getUserId(), user.getUsername(), user.getName(), user.getRole()));
    }

    public record LoginUser(Long userId, String username, String name, Role role) {
    }
}
