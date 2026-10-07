package com.pharmlink.backend.domain.auth.dto;

// 토큰 재발급 응답
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    public static final String TOKEN_TYPE = "Bearer";

    public static TokenResponse of(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, TOKEN_TYPE, expiresIn);
    }
}
