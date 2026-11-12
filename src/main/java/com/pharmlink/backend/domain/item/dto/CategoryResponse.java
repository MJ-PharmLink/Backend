package com.pharmlink.backend.domain.item.dto;

import com.pharmlink.backend.domain.item.entity.Category;

import java.time.Instant;

public record CategoryResponse(
        Long categoryId,
        String categoryName,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getCategoryId(),
                category.getCategoryName(),
                category.getDescription(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
