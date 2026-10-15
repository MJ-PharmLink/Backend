package com.pharmlink.backend.global.exception;

import lombok.Getter;

import java.util.List;

// 서비스 계층에서 업무 규칙 위반 시 throw new BusinessException(ErrorCode.XXX)
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    // 오류 응답 error.details에 그대로 담을 항목 (예: INSUFFICIENT_STOCK의 상품별 요청·가용 수량)
    private final List<?> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), List.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<?> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }
}
