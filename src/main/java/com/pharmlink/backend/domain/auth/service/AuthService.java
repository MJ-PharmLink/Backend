package com.pharmlink.backend.domain.auth.service;

import com.pharmlink.backend.domain.auth.dto.LoginRequest;
import com.pharmlink.backend.domain.auth.dto.LoginResponse;
import com.pharmlink.backend.domain.auth.dto.MeResponse;
import com.pharmlink.backend.domain.auth.dto.TokenResponse;
import com.pharmlink.backend.domain.auth.entity.RefreshToken;
import com.pharmlink.backend.domain.auth.repository.RefreshTokenRepository;
import com.pharmlink.backend.domain.user.entity.User;
import com.pharmlink.backend.domain.user.repository.UserRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import com.pharmlink.backend.global.security.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final Duration refreshTokenValidity;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       RefreshTokenGenerator refreshTokenGenerator,
                       @Value("${jwt.refresh-expiration}") long refreshExpirationMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.refreshTokenValidity = Duration.ofMillis(refreshExpirationMs);
    }

    // 로그인: 사용자당 refresh token 1건 유지, 다시 로그인하면 이전 refresh token은 무효
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        // 비밀번호가 맞을 때만 비활성 여부를 알려준다 (계정 존재 여부 노출 방지)
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String refreshToken = refreshTokenGenerator.generate();
        String tokenHash = refreshTokenGenerator.hash(refreshToken);
        Instant expiresAt = Instant.now().plus(refreshTokenValidity);
        refreshTokenRepository.findByUserUserId(user.getUserId())
                .ifPresentOrElse(
                        saved -> saved.rotate(tokenHash, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.create(user, tokenHash, expiresAt)));

        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getRole());
        return LoginResponse.of(accessToken, refreshToken, jwtProvider.getAccessTokenExpiresIn(), user);
    }

    // 토큰 재발급: refresh token은 그대로 두고 access token만 새로 발급
    public TokenResponse refresh(String refreshToken) {
        RefreshToken saved = refreshTokenRepository.findByTokenHash(refreshTokenGenerator.hash(refreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));
        if (saved.getRevokedAt() != null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (!saved.getExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        User user = saved.getUser();
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        String accessToken = jwtProvider.createAccessToken(user.getUserId(), user.getRole());
        return TokenResponse.of(accessToken, jwtProvider.getAccessTokenExpiresIn());
    }

    // 로그아웃
    @Transactional
    public void logout(Long userId) {
        revokeRefreshToken(userId);
    }

    // 내 정보 조회
    public MeResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return MeResponse.from(user);
    }

    // 사용자 관리(비밀번호·역할 변경, 비활성화)에서도 호출해 refresh token을 폐기한다
    @Transactional
    public void revokeRefreshToken(Long userId) {
        refreshTokenRepository.findByUserUserId(userId)
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(RefreshToken::revoke);
    }
}
