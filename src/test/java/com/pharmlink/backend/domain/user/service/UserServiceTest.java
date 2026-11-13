package com.pharmlink.backend.domain.user.service;

import com.pharmlink.backend.domain.auth.service.AuthService;
import com.pharmlink.backend.domain.user.dto.UserUpdateRequest;
import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.entity.User;
import com.pharmlink.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// UserService 단위 테스트 (DB 없이 Repository를 Mock으로 대체)
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthService authService;

    @InjectMocks
    private UserService userService;

    private User target;

    @BeforeEach
    void setUp() {
        target = User.create("qa_sales", "encoded-password", "테스트 영업", Role.SALES);
        ReflectionTestUtils.setField(target, "userId", TARGET_ID);
        given(userRepository.findById(TARGET_ID)).willReturn(Optional.of(target));
    }

    @Test
    @DisplayName("역할을 바꾸면 해당 사용자의 refresh token을 폐기한다")
    void roleChangeRevokesRefreshToken() {
        userService.updateUser(ADMIN_ID, TARGET_ID, new UserUpdateRequest(null, Role.WAREHOUSE, null, null));

        assertThat(target.getRole()).isEqualTo(Role.WAREHOUSE);
        verify(authService).revokeRefreshToken(TARGET_ID);
    }

    @Test
    @DisplayName("같은 역할을 다시 보내거나 이름만 바꾸면 refresh token을 폐기하지 않는다")
    void sameRoleDoesNotRevokeRefreshToken() {
        userService.updateUser(ADMIN_ID, TARGET_ID, new UserUpdateRequest("새 이름", Role.SALES, null, null));

        assertThat(target.getName()).isEqualTo("새 이름");
        verify(authService, never()).revokeRefreshToken(anyLong());
    }
}
