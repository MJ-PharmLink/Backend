package com.pharmlink.backend.global.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class Pagination {
    private int page;
    private int pageSize;
    private long totalCount;
    private int totalPages;

    // Spring Data Page(0부터 시작) → API 페이지(1부터 시작)
    public static Pagination of(Page<?> page) {
        return Pagination.builder()
                .page(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalCount(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
