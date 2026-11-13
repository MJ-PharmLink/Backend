package com.pharmlink.backend.global.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {

    private boolean success;
    private ErrorDetail error;
    private Meta meta;

    public static ErrorResponse of(String code, String message) {
        return of(code, message, Collections.emptyList());
    }

    public static ErrorResponse of(String code, String message, List<?> details) {
        return ErrorResponse.builder()
                .success(false)
                .error(ErrorDetail.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .build())
                .meta(Meta.now())
                .build();
    }

    @Getter
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;
        // 필드 검증 오류는 FieldError, 업무 오류는 코드별 상세 항목
        private List<?> details;
    }

    @Getter
    @Builder
    public static class FieldError {
        private String field;
        private String issue;
    }
}