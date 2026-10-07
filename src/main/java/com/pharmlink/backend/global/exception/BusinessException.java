package com.pharmlink.backend.global.exception;

import lombok.Getter;

// 서비스 계층에서 업무 규칙 위반 시 throw new BusinessException(ErrorCode.XXX)
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
