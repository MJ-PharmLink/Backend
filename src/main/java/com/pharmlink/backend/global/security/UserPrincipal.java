package com.pharmlink.backend.global.security;

import com.pharmlink.backend.domain.user.entity.Role;

// 로그인 사용자 정보. 컨트롤러에서 @AuthenticationPrincipal UserPrincipal principal 로 받는다.
// access token에 담긴 값만 사용하므로 DB 조회 없이 꺼낼 수 있다.
public record UserPrincipal(Long userId, Role role) {
}
