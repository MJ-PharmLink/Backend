package com.pharmlink.backend.domain.auth.service;

import com.pharmlink.backend.domain.auth.dto.LoginRequest;
import com.pharmlink.backend.domain.auth.dto.LoginResponse;
import com.pharmlink.backend.domain.auth.entity.RefreshToken;
import com.pharmlink.backend.domain.auth.repository.RefreshTokenRepository;
import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.entity.User;
import com.pharmlink.backend.domain.user.repository.UserRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import com.pharmlink.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// AuthService 단위 테스트 (DB 없이 Repository를 Mock으로 대체)
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final long REFRESH_EXPIRATION_MS = 14L * 24 * 60 * 60 * 1000;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;

    private final RefreshTokenGenerator refreshTokenGenerator = new RefreshTokenGenerator();
    private AuthService authService;
    private User user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtProvider,
                refreshTokenGenerator, REFRESH_EXPIRATION_MS);
        user = User.create("qa_sales", "encoded-password", "테스트 영업", Role.SALES);
        ReflectionTestUtils.setField(user, "userId", 2L);
    }

    @Test
    @DisplayName("다시 로그인하면 이전 refresh token은 401 INVALID_TOKEN, 새 refresh token은 재발급 성공")
    void reloginInvalidatesPreviousRefreshToken() {
        given(userRepository.findByUsername("qa_sales")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded-password")).willReturn(true);
        given(jwtProvider.createAccessToken(any(), any())).willReturn("access-token");

        // 첫 로그인: 저장된 refresh token이 없어서 새로 저장
        given(refreshTokenRepository.findByUserUserId(2L)).willReturn(Optional.empty());
        LoginResponse first = authService.login(new LoginRequest("qa_sales", "password123!"));
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        // 두 번째 로그인: 같은 행을 새 토큰으로 교체
        given(refreshTokenRepository.findByUserUserId(2L)).willReturn(Optional.of(saved));
        LoginResponse second = authService.login(new LoginRequest("qa_sales", "password123!"));

        // DB처럼 현재 저장된 해시와 같은 토큰만 찾아준다
        given(refreshTokenRepository.findByTokenHash(anyString())).willAnswer(invocation ->
                saved.getTokenHash().equals(invocation.getArgument(0)) ? Optional.of(saved) : Optional.empty());

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThatThrownBy(() -> authService.refresh(first.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_TOKEN);
        assertThat(authService.refresh(second.refreshToken()).accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("비활성 계정은 비밀번호가 맞으면 403 ACCOUNT_DISABLED, 토큰을 발급하지 않음")
    void inactiveAccountCannotLogin() {
        user.deactivate();
        given(userRepository.findByUsername("qa_sales")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password123!", "encoded-password")).willReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("qa_sales", "password123!")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ACCOUNT_DISABLED);
        verify(refreshTokenRepository, never()).save(any());
        verify(jwtProvider, never()).createAccessToken(any(), any());
    }

    @Test
    @DisplayName("비활성 계정이라도 비밀번호가 틀리면 401 INVALID_CREDENTIALS (계정 상태 노출 안 함)")
    void inactiveAccountWithWrongPassword() {
        user.deactivate();
        given(userRepository.findByUsername("qa_sales")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("qa_sales", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }
}
