package com.pharmlink.backend.global.security.jwt;

import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.global.security.UserPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// access token 발급·검증 (jjwt 0.12.6, HS256)
@Component
public class JwtProvider {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long accessTokenExpirationMs;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration}") long accessTokenExpirationMs) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret은 32바이트(영문 32자) 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpirationMs = accessTokenExpirationMs;
    }

    // subject = user_id, role 클레임 = ADMIN / SALES / WAREHOUSE
    public String createAccessToken(Long userId, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenExpirationMs))
                .signWith(key)
                .compact();
    }

    // 서명·만료 검증 후 principal 반환. 실패 시 JwtException(만료는 ExpiredJwtException)
    public UserPrincipal parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            Long userId = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.get(ROLE_CLAIM, String.class));
            return new UserPrincipal(userId, role);
        } catch (RuntimeException e) {
            throw new JwtException("토큰의 user_id 또는 role 값이 올바르지 않습니다.", e);
        }
    }

    // 로그인/재발급 응답의 expires_in (초)
    public long getAccessTokenExpiresIn() {
        return accessTokenExpirationMs / 1000;
    }
}
