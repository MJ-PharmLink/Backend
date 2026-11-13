package com.pharmlink.backend.global.security;

// 역할별 접근 제어 표현식 
// 사용법: @PreAuthorize(AccessRole.ADMIN_SALES)
public final class AccessRole {

    // 관리자
    public static final String ADMIN = "hasRole('ADMIN')";

    // 관리자, 영업
    public static final String ADMIN_SALES = "hasAnyRole('ADMIN', 'SALES')";

    // 관리자, 창고
    public static final String ADMIN_WAREHOUSE = "hasAnyRole('ADMIN', 'WAREHOUSE')";

    // 관리자, 영업, 창고 / 로그인 사용자
    public static final String ALL = "hasAnyRole('ADMIN', 'SALES', 'WAREHOUSE')";

    private AccessRole() {
    }
}
