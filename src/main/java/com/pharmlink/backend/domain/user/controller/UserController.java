package com.pharmlink.backend.domain.user.controller;

import com.pharmlink.backend.domain.user.dto.UserCreateRequest;
import com.pharmlink.backend.domain.user.dto.UserCreateResponse;
import com.pharmlink.backend.domain.user.dto.UserResponse;
import com.pharmlink.backend.domain.user.dto.UserUpdateRequest;
import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.domain.user.service.UserService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import com.pharmlink.backend.global.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 사용자(계정) 관리 API — 전체 관리자 전용
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize(AccessRole.ADMIN)
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 사용자 목록 조회
    @GetMapping
    public ApiResponse<List<UserResponse>> getUsers(
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) Role role,
            @RequestParam(name = "include_inactive", defaultValue = "false") boolean includeInactive) {
        return ApiResponse.success(userService.getUsers(page, pageSize, role, includeInactive));
    }

    // 사용자 계정 생성 → 201 Created
    @PostMapping
    public ResponseEntity<ApiResponse<UserCreateResponse>> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(request)));
    }

    // 사용자 정보/역할 수정 (본인 계정의 역할 변경·비활성화는 422)
    @PatchMapping("/{userId}")
    public ApiResponse<UserResponse> updateUser(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable Long userId,
                                                @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.success(userService.updateUser(principal.userId(), userId, request));
    }

    // 사용자 삭제(비활성화) → 204 No Content (본인 계정은 422)
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deactivateUser(@AuthenticationPrincipal UserPrincipal principal,
                                               @PathVariable Long userId) {
        userService.deactivateUser(principal.userId(), userId);
        return ResponseEntity.noContent().build();
    }
}
