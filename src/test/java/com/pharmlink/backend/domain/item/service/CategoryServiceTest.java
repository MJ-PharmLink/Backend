
package com.pharmlink.backend.domain.item.service;

import com.pharmlink.backend.domain.item.dto.CategoryCreateRequest;
import com.pharmlink.backend.domain.item.dto.CategoryResponse;
import com.pharmlink.backend.domain.item.dto.CategoryUpdateRequest;
import com.pharmlink.backend.domain.item.entity.Category;
import com.pharmlink.backend.domain.item.repository.CategoryRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Long CATEGORY_ID = 3L;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    @DisplayName("카테고리 목록 조회: 등록된 카테고리를 반환한다")
    void getCategoriesSuccess() {

        Category category1 = new Category(
                "감염성질환 및 호흡기계",
                "감기, 알레르기 등"
        );

        Category category2 = new Category(
                "신경계 및 정신/행동장애",
                "해열, 진통, 두통 등"
        );

        given(categoryRepository.findAll())
                .willReturn(List.of(category1, category2));

        List<CategoryResponse> responses =
                categoryService.getCategories();

        assertThat(responses).hasSize(2);

        assertThat(responses.get(0).categoryName())
                .isEqualTo("감염성질환 및 호흡기계");

        assertThat(responses.get(1).categoryName())
                .isEqualTo("신경계 및 정신/행동장애");

        verify(categoryRepository).findAll();
    }

    @Test
    @DisplayName("카테고리 등록: 정상 요청이면 저장된다")
    void createCategorySuccess() {

        CategoryCreateRequest request =
                new CategoryCreateRequest(
                        "테스트 카테고리",
                        "단위 테스트용 카테고리"
                );

        given(categoryRepository.saveAndFlush(any(Category.class)))
                .willAnswer(invocation ->
                        invocation.getArgument(0)
                );

        CategoryResponse response =
                categoryService.createCategory(request);

        assertThat(response.categoryName())
                .isEqualTo("테스트 카테고리");

        assertThat(response.description())
                .isEqualTo("단위 테스트용 카테고리");

        verify(categoryRepository)
                .existsByCategoryName("테스트 카테고리");

        verify(categoryRepository)
                .saveAndFlush(any(Category.class));
    }

    @Test
    @DisplayName("카테고리 등록: 이름 앞뒤 공백을 제거한다")
    void createCategoryTrimsName() {

        CategoryCreateRequest request =
                new CategoryCreateRequest(
                        "  테스트 카테고리  ",
                        "공백 제거 확인"
                );

        given(categoryRepository.saveAndFlush(any(Category.class)))
                .willAnswer(invocation ->
                        invocation.getArgument(0)
                );

        CategoryResponse response =
                categoryService.createCategory(request);

        assertThat(response.categoryName())
                .isEqualTo("테스트 카테고리");

        verify(categoryRepository)
                .existsByCategoryName("테스트 카테고리");
    }

    @Test
    @DisplayName("카테고리 등록: 중복 이름이면 CATEGORY_ALREADY_EXISTS")
    void createCategoryWithDuplicateName() {

        given(categoryRepository.existsByCategoryName("기타"))
                .willReturn(true);

        CategoryCreateRequest request =
                new CategoryCreateRequest(
                        "기타",
                        "중복 등록 테스트"
                );

        assertThatThrownBy(() ->
                categoryService.createCategory(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_ALREADY_EXISTS);

        verify(categoryRepository, never())
                .saveAndFlush(any(Category.class));
    }

    @Test
    @DisplayName("카테고리 수정: 정상 요청이면 이름과 설명이 변경된다")
    void updateCategorySuccess() {

        Category category = category();

        given(categoryRepository.findById(CATEGORY_ID))
                .willReturn(Optional.of(category));

        given(categoryRepository.saveAndFlush(category))
                .willReturn(category);

        CategoryUpdateRequest request =
                new CategoryUpdateRequest(
                        "신경계 의약품",
                        "해열제, 진통제, 두통약"
                );

        CategoryResponse response =
                categoryService.updateCategory(CATEGORY_ID, request);

        assertThat(response.categoryName())
                .isEqualTo("신경계 의약품");

        assertThat(response.description())
                .isEqualTo("해열제, 진통제, 두통약");

        assertThat(category.getCategoryName())
                .isEqualTo("신경계 의약품");

        verify(categoryRepository)
                .saveAndFlush(category);
    }

    @Test
    @DisplayName("카테고리 수정: 존재하지 않으면 CATEGORY_NOT_FOUND")
    void updateCategoryNotFound() {

        given(categoryRepository.findById(999L))
                .willReturn(Optional.empty());

        CategoryUpdateRequest request =
                new CategoryUpdateRequest(
                        "수정 테스트",
                        "존재하지 않는 카테고리"
                );

        assertThatThrownBy(() ->
                categoryService.updateCategory(999L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);

        verify(categoryRepository, never())
                .saveAndFlush(any(Category.class));
    }

    @Test
    @DisplayName("카테고리 수정: 다른 카테고리와 이름이 중복되면 오류")
    void updateCategoryWithDuplicateName() {

        Category category = category();

        given(categoryRepository.findById(CATEGORY_ID))
                .willReturn(Optional.of(category));

        given(
                categoryRepository.existsByCategoryNameAndCategoryIdNot(
                        "기타",
                        CATEGORY_ID
                )
        ).willReturn(true);

        CategoryUpdateRequest request =
                new CategoryUpdateRequest(
                        "기타",
                        "중복 이름 수정 테스트"
                );

        assertThatThrownBy(() ->
                categoryService.updateCategory(CATEGORY_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_ALREADY_EXISTS);

        assertThat(category.getCategoryName())
                .isEqualTo("신경계 및 정신/행동장애");

        verify(categoryRepository, never())
                .saveAndFlush(any(Category.class));
    }

    // 테스트용 카테고리 생성
    private Category category() {

        Category category = new Category(
                "신경계 및 정신/행동장애",
                "해열, 진통, 두통 등"
        );

        ReflectionTestUtils.setField(
                category,
                "categoryId",
                CATEGORY_ID
        );

        return category;
    }
}

