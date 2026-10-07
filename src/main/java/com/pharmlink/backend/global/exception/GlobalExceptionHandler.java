package com.pharmlink.backend.global.exception;

import com.pharmlink.backend.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.InvalidFormatException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 서비스에서 던진 업무 예외
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return toResponse(e.getErrorCode(), e.getMessage(), List.of());
    }

    // @Valid 검증 실패 (@RequestBody, @ModelAttribute) → 400 VALIDATION_ERROR
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        List<ErrorResponse.FieldError> details = e.getBindingResult().getFieldErrors().stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(toSnakeCase(error.getField()))
                        .issue(toSnakeCase(error.getCode()))
                        .build())
                .toList();
        return toResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details);
    }

    // @RequestParam, @PathVariable 등 메서드 파라미터 검증 실패 → 400 VALIDATION_ERROR
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        List<ErrorResponse.FieldError> details = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> ErrorResponse.FieldError.builder()
                                .field(result.getMethodParameter().getParameterName())
                                .issue(error.getCodes() == null ? null : toSnakeCase(lastCode(error.getCodes())))
                                .build()))
                .toList();
        return toResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(), details);
    }

    // 필수 쿼리 파라미터 누락 → 400 VALIDATION_ERROR
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return toResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                List.of(fieldError(e.getParameterName(), "required")));
    }

    // 쿼리 파라미터 타입 오류. enum 값 오류(role=ABC 등)는 422, 그 외 형식 오류는 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> requiredType = e.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            return toResponse(ErrorCode.UNPROCESSABLE_ENTITY, ErrorCode.UNPROCESSABLE_ENTITY.getMessage(),
                    List.of(fieldError(e.getName(), "invalid_value")));
        }
        return toResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                List.of(fieldError(e.getName(), "invalid_format")));
    }

    // 요청 본문 JSON 오류. enum 값 오류는 422, 그 외(JSON 문법, 타입 오류)는 400
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        Throwable cause = e.getCause();
        while (cause != null) {
            if (cause instanceof InvalidFormatException invalidFormat) {
                String field = invalidFormat.getPath().isEmpty()
                        ? null
                        : invalidFormat.getPath().get(invalidFormat.getPath().size() - 1).getPropertyName();
                if (invalidFormat.getTargetType() != null && invalidFormat.getTargetType().isEnum()) {
                    return toResponse(ErrorCode.UNPROCESSABLE_ENTITY, ErrorCode.UNPROCESSABLE_ENTITY.getMessage(),
                            List.of(fieldError(field, "invalid_value")));
                }
                return toResponse(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getMessage(),
                        List.of(fieldError(field, "invalid_format")));
            }
            cause = cause.getCause();
        }
        return toResponse(ErrorCode.VALIDATION_ERROR, "요청 본문을 읽을 수 없습니다.", List.of());
    }

    // @PreAuthorize 등 메서드 보안에서 권한 부족 → 403 FORBIDDEN
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return toResponse(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getMessage(), List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException e) {
        return toResponse(ErrorCode.UNAUTHORIZED, ErrorCode.UNAUTHORIZED.getMessage(), List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return toResponse(ErrorCode.RESOURCE_NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.getMessage(), List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return toResponse(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage(), List.of());
    }

    // 처리되지 않은 예외 → 500 INTERNAL_SERVER_ERROR (내부 메시지는 응답에 노출하지 않음)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return toResponse(ErrorCode.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_SERVER_ERROR.getMessage(), List.of());
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, String message,
                                                     List<ErrorResponse.FieldError> details) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode.name(), message, details));
    }

    private ErrorResponse.FieldError fieldError(String field, String issue) {
        return ErrorResponse.FieldError.builder()
                .field(field)
                .issue(issue)
                .build();
    }

    private String lastCode(String[] codes) {
        return codes.length == 0 ? null : codes[codes.length - 1];
    }

    // unitPrice → unit_price, NotBlank → not_blank
    private String toSnakeCase(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
