package com.pharmlink.backend.domain.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryUpdateRequest(

        @NotBlank(message = "카테고리 이름은 필수입니다.")
        @Size(max = 50, message = "카테고리 이름은 50자 이하여야 합니다.")
        String categoryName,

        @Size(max = 200, message = "카테고리 설명은 200자 이하여야 합니다.")
        String description

) {
}
