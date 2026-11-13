package com.pharmlink.backend.domain.order.dto;

import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 주문 등록(8.2)·매입 등록(10.2) 요청의 Bean Validation. items 안의 null 원소는 400 VALIDATION_ERROR여야 한다
class CreateRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("주문 등록: items에 null 원소가 있으면 검증 실패 (서비스까지 가서 500이 나지 않음)")
    void orderItemsWithNullElement() {
        OrderCreateRequest request = new OrderCreateRequest(62L, Arrays.asList(new OrderCreateRequest.Line(301L, 1), null));

        Set<ConstraintViolation<OrderCreateRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString())
                .singleElement().asString().startsWith("items[1]");
    }

    @Test
    @DisplayName("매입 등록: items에 null 원소가 있으면 검증 실패")
    void purchaseItemsWithNullElement() {
        PurchaseCreateRequest request = new PurchaseCreateRequest(61L, null, LocalDate.of(2026, 10, 18),
                Arrays.asList((PurchaseCreateRequest.Line) null));

        Set<ConstraintViolation<PurchaseCreateRequest>> violations = validator.validate(request);

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).startsWith("items[0]");
    }

    @Test
    @DisplayName("주문 등록: 정상 요청은 위반 없음")
    void validOrderRequest() {
        OrderCreateRequest request = new OrderCreateRequest(62L, Arrays.asList(new OrderCreateRequest.Line(301L, 1)));

        assertThat(validator.validate(request)).isEmpty();
    }
}
