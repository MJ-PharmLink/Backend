package com.pharmlink.backend.domain.ai.tool;

import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

// AI 조회 도구 공통 권한 확인
// AccessDeniedException을 던지면 GlobalExceptionHandler에서 403이 되므로,
// 미인증은 401 UNAUTHORIZED, 역할 부족은 403 FORBIDDEN 으로 직접 구분한다.
final class AiAuthorization {

    private AiAuthorization() {
    }

    static Authentication requireAuthenticated() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return authentication;
    }

    // 기존 ERP API의 @PreAuthorize(AccessRole.*) 와 같은 역할만 허용한다.
    static void requireAnyRole(String... roles) {
        Authentication authentication = requireAuthenticated();

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            for (String role : roles) {
                if (("ROLE_" + role).equals(authority.getAuthority())) {
                    return;
                }
            }
        }

        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
