
package com.pharmlink.backend.domain.item.controller;

import com.pharmlink.backend.domain.item.dto.CategoryCreateRequest;
import com.pharmlink.backend.domain.item.dto.CategoryResponse;
import com.pharmlink.backend.domain.item.dto.CategoryUpdateRequest;
import com.pharmlink.backend.domain.item.service.CategoryService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // 카테고리 목록 조회
    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getCategories() {
        return ApiResponse.success(categoryService.getCategories());
    }

    // 카테고리 등록
    @PreAuthorize(AccessRole.ADMIN)
    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryCreateRequest request
    ) {
        return ApiResponse.success(
                categoryService.createCategory(request)
        );
    }

    // 카테고리 수정
    @PreAuthorize(AccessRole.ADMIN)
    @PatchMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable("id") Long categoryId,
            @Valid @RequestBody CategoryUpdateRequest request
    ) {
        return ApiResponse.success(
                categoryService.updateCategory(categoryId, request)
        );
    }
}


