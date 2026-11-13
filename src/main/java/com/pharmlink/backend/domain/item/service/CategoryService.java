
package com.pharmlink.backend.domain.item.service;

import com.pharmlink.backend.domain.item.dto.CategoryCreateRequest;
import com.pharmlink.backend.domain.item.dto.CategoryResponse;
import com.pharmlink.backend.domain.item.dto.CategoryUpdateRequest;
import com.pharmlink.backend.domain.item.entity.Category;
import com.pharmlink.backend.domain.item.repository.CategoryRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // 카테고리 목록 조회
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(CategoryResponse::from)
                .toList();
    }

    // 카테고리 등록
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        String categoryName = request.categoryName().trim();

        if (categoryRepository.existsByCategoryName(categoryName)) {
            throw new BusinessException(ErrorCode.CATEGORY_ALREADY_EXISTS);
        }

        Category category = new Category(
                categoryName,
                request.description()
        );

        return CategoryResponse.from(
                categoryRepository.saveAndFlush(category)
        );
    }

    // 카테고리 수정
    @Transactional
    public CategoryResponse updateCategory(
            Long categoryId,
            CategoryUpdateRequest request
    ) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.CATEGORY_NOT_FOUND)
                );

        String categoryName = request.categoryName().trim();

        if (categoryRepository.existsByCategoryNameAndCategoryIdNot(
                categoryName,
                categoryId
        )) {
            throw new BusinessException(ErrorCode.CATEGORY_ALREADY_EXISTS);
        }

        category.update(
                categoryName,
                request.description()
        );

        return CategoryResponse.from(
                categoryRepository.saveAndFlush(category)
        );
    }
}
