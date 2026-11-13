package com.pharmlink.backend.domain.auth.entity;

import com.pharmlink.backend.domain.user.entity.User;
import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// refresh_tokens — 사용자당 1건, 토큰 원문이 아닌 SHA-256 해시만 저장
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_tokens",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rt_user_id", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_rt_token_hash", columnNames = "token_hash")
        },
        indexes = @Index(name = "idx_rt_expires", columnList = "expires_at"))
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long tokenId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    public static RefreshToken create(User user, String tokenHash, Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.user = user;
        refreshToken.tokenHash = tokenHash;
        refreshToken.expiresAt = expiresAt;
        return refreshToken;
    }

    // 재로그인 시 기존 행을 새 토큰으로 교체 (사용자당 1건 유지, 이전 토큰은 무효)
    public void rotate(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revokedAt = null;
    }

    // 로그아웃
    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public boolean isUsable() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
