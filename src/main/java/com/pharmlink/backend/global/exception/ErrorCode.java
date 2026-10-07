package com.pharmlink.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

// API 명세서 16장 Error Code 전체 목록
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    // 401
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 일치하지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // 403
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "비활성화된 계정입니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "회사 정보가 존재하지 않습니다."),
    PARTNER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 거래처입니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 의약품을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 카테고리입니다."),
    WAREHOUSE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 창고입니다."),
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 재고입니다."),
    LOT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 로트입니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 주문입니다."),
    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 납품입니다."),
    PURCHASE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매입 기록입니다."),
    SALE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 매출 기록입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),

    // 405
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    // 409
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "이미 존재하는 username입니다."),
    DUPLICATE_BUSINESS_NUMBER(HttpStatus.CONFLICT, "이미 등록된 사업자등록번호입니다."),
    ITEM_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 상품 코드입니다."),
    CATEGORY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 존재하는 카테고리명입니다."),
    PARTNER_IN_USE(HttpStatus.CONFLICT, "진행 중인 주문/거래가 있어 삭제할 수 없습니다."),
    PARTNER_INACTIVE(HttpStatus.CONFLICT, "비활성화된 거래처입니다."),
    ITEM_INACTIVE(HttpStatus.CONFLICT, "단종된 상품입니다."),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "가용 재고가 부족합니다."),
    ORDER_ALREADY_APPROVED(HttpStatus.CONFLICT, "이미 승인된 주문입니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
    INVALID_DELIVERY_STATUS(HttpStatus.CONFLICT, "현재 납품 상태에서는 처리할 수 없습니다."),
    SALE_ALREADY_RECORDED(HttpStatus.CONFLICT, "이미 매출 처리된 납품입니다."),

    // 422
    INVALID_QUANTITY(HttpStatus.UNPROCESSABLE_CONTENT, "수량 값이 올바르지 않습니다."),
    INVALID_PARTNER_TYPE(HttpStatus.UNPROCESSABLE_CONTENT, "거래처 유형이 일치하지 않습니다."),
    INVALID_DATE_RANGE(HttpStatus.UNPROCESSABLE_CONTENT, "조회 기간 값이 올바르지 않습니다."),
    INVALID_EXPIRY_DATE(HttpStatus.UNPROCESSABLE_CONTENT, "유통기한 값이 올바르지 않습니다."),
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_CONTENT, "요청 값을 처리할 수 없습니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    INVENTORY_UPDATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "재고 증감 처리에 실패했습니다."),
    SALE_CREATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "매출 기록 생성에 실패했습니다."),
    PDF_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PDF 생성에 실패했습니다."),
    INVENTORY_QUERY_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "재고 조회에 실패했습니다.");

    private final HttpStatus status;
    private final String message;
}
