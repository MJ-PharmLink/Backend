package com.pharmlink.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

@Getter
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;

    // 목록 API에서만 포함, 단건 응답에서는 생략
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Pagination pagination;

    private Meta meta;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .meta(Meta.now())
                .build();
    }

    public static <T> ApiResponse<List<T>> success(Page<T> page) {
        return ApiResponse.<List<T>>builder()
                .success(true)
                .data(page.getContent())
                .pagination(Pagination.of(page))
                .meta(Meta.now())
                .build();
    }
}
