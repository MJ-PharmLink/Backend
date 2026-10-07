package com.pharmlink.backend.domain.auth.controller;

import com.pharmlink.backend.domain.auth.dto.LoginRequest;
import com.pharmlink.backend.domain.auth.dto.LoginResponse;
import com.pharmlink.backend.domain.auth.dto.MeResponse;
import com.pharmlink.backend.domain.auth.dto.RefreshRequest;
import com.pharmlink.backend.domain.auth.dto.TokenResponse;
import com.pharmlink.backend.domain.auth.service.AuthService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import com.pharmlink.backend.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 인증 API
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 로그인 (권한: 전체)
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    // 토큰 재발급 (권한: 전체)
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request.refreshToken()));
    }

    // 로그아웃 (권한: 로그인 사용자) → 204 No Content
    @PreAuthorize(AccessRole.ALL)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal.userId());
        return ResponseEntity.noContent().build();
    }

    // 내 정보 조회 (권한: 로그인 사용자)
    @PreAuthorize(AccessRole.ALL)
    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success(authService.getMe(principal.userId()));
    }
}
