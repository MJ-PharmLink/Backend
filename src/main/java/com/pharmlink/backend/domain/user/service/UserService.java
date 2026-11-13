package com.pharmlink.backend.domain.user.service;

import com.pharmlink.backend.domain.auth.service.AuthService;
import com.pharmlink.backend.domain.user.dto.UserCreateRequest;
import com.pharmlink.backend.domain.user.dto.UserCreateResponse;
import com.pharmlink.backend.domain.user.dto.UserResponse;
import com.pharmlink.backend.domain.user.dto.UserUpdateRequest;
import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.entity.User;
import com.pharmlink.backend.domain.user.repository.UserRepository;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserService {

    // 관리자가 본인 역할을 바꾸거나 본인을 비활성화하면 활성 관리자가 0명이 될 수 있어 막는다
    private static final String SELF_CHANGE_NOT_ALLOWED = "본인 계정의 역할 변경이나 비활성화는 할 수 없습니다.";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    // 사용자 목록 (가입 순)
    public Page<UserResponse> getUsers(Integer page, Integer pageSize, Role role, boolean includeInactive) {
        return userRepository.search(role, includeInactive,
                        PageRequestFactory.of(page, pageSize, Sort.by("userId")))
                .map(UserResponse::from);
    }

    // 사용자 계정 생성
    @Transactional
    public UserCreateResponse createUser(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
        User user = User.create(request.username(), passwordEncoder.encode(request.password()),
                request.name(), request.role());
        return UserCreateResponse.from(userRepository.saveAndFlush(user));
    }

    // 사용자 정보/역할 수정
    // 비밀번호·역할 변경 또는 활성→비활성 전환 시 같은 트랜잭션에서 refresh token을 폐기한다
    @Transactional
    public UserResponse updateUser(Long loginUserId, Long userId, UserUpdateRequest request) {
        User user = findUser(userId);
        boolean roleChanged = request.role() != null && request.role() != user.getRole();
        boolean deactivating = Boolean.FALSE.equals(request.isActive()) && user.isActive();
        if (userId.equals(loginUserId) && (roleChanged || deactivating)) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY, SELF_CHANGE_NOT_ALLOWED);
        }
        boolean revokeToken = false;

        if (request.name() != null) {
            user.changeName(request.name());
        }
        if (roleChanged) {
            user.changeRole(request.role());
            revokeToken = true;
        }
        if (request.password() != null) {
            user.changePassword(passwordEncoder.encode(request.password()));
            revokeToken = true;
        }
        if (deactivating) {
            user.deactivate();
            revokeToken = true;
        } else if (Boolean.TRUE.equals(request.isActive())) {
            user.activate();
        }

        if (revokeToken) {
            authService.revokeRefreshToken(userId);
        }
        return UserResponse.from(user);
    }

    // 사용자 삭제(비활성화): 소프트 삭제 + refresh token 폐기
    @Transactional
    public void deactivateUser(Long loginUserId, Long userId) {
        User user = findUser(userId);
        if (userId.equals(loginUserId)) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY, SELF_CHANGE_NOT_ALLOWED);
        }
        user.deactivate();
        authService.revokeRefreshToken(userId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
