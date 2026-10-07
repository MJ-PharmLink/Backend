package com.pharmlink.backend.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 초기 관리자 계정 생성. 계정 정보는 git에 올리지 않고 application-secret.yml(admin.*)에서 읽는다.
// ADMIN 계정이 하나도 없을 때만 1회 생성한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${admin.username:}")
    private String username;

    @Value("${admin.password:}")
    private String password;

    @Value("${admin.name:관리자}")
    private String name;

    @Override
    public void run(ApplicationArguments args) {
        Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'", Integer.class);
        if (adminCount != null && adminCount > 0) {
            return;
        }
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("ADMIN 계정이 없습니다. application-secret.yml에 admin.username, admin.password를 설정하면 실행 시 생성됩니다.");
            return;
        }
        if (password.length() < 8 || password.length() > 64) {
            throw new IllegalStateException("admin.password는 8~64자여야 합니다.");
        }
        jdbcTemplate.update(
                "INSERT INTO users (username, password, name, role, is_active) VALUES (?, ?, ?, 'ADMIN', TRUE)",
                username, new BCryptPasswordEncoder().encode(password), name);
        log.info("초기 ADMIN 계정을 생성했습니다. username={}", username);
    }
}
