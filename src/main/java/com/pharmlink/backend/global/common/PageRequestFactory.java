package com.pharmlink.backend.global.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

// 공통 페이지네이션 규칙: page 기본 1, page_size 기본 20, 최대 100 (초과 시 100으로 절삭)
public final class PageRequestFactory {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private PageRequestFactory() {
    }

    public static Pageable of(Integer page, Integer pageSize) {
        return of(page, pageSize, Sort.unsorted());
    }

    public static Pageable of(Integer page, Integer pageSize, Sort sort) {
        int resolvedPage = (page == null || page < 1) ? DEFAULT_PAGE : page;
        int resolvedSize = (pageSize == null || pageSize < 1) ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return PageRequest.of(resolvedPage - 1, resolvedSize, sort);
    }
}
