package com.pharmlink.backend.domain.delivery.service;

import com.pharmlink.backend.domain.delivery.dto.DeliveryCompleteResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryDetailResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryShipResponse;
import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.delivery.repository.DeliveryRepository;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;
import com.pharmlink.backend.domain.sales.entity.Sale;
import com.pharmlink.backend.domain.sales.repository.SaleRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// DeliveryService 단위 테스트 (DB 없이 Repository를 Mock으로 대체)
@ExtendWith(MockitoExtension.class)
class DeliveryServiceTest {

    private static final Long DELIVERY_ID = 35L;
    private static final Long ORDER_ID = 88L;
    private static final Long PARTNER_ID = 10L;

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private SaleRepository saleRepository;
    @InjectMocks
    private DeliveryService deliveryService;

    @Test
    @DisplayName("목록: 조건을 그대로 넘기고 KST 날짜 경계·최신순으로 조회, 거래처명·주문번호·총액은 주문에서")
    void listUsesKstBoundariesAndLatestFirst() {
        Delivery delivery = waitingDelivery();
        given(deliveryRepository.search(eq(DeliveryStatus.WAITING), eq(PARTNER_ID), any(), any(), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(delivery)));

        Page<DeliveryResponse> page = deliveryService.getDeliveries(DeliveryStatus.WAITING, PARTNER_ID,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 1, 20);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(deliveryRepository).search(any(), any(), from.capture(), to.capture(), pageable.capture());
        assertThat(from.getValue()).isEqualTo(Instant.parse("2026-08-31T15:00:00Z"));
        assertThat(to.getValue()).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("deliveryId")));
        DeliveryResponse row = page.getContent().get(0);
        assertThat(row.orderNumber()).isEqualTo("ORD-20260910-0088");
        assertThat(row.partnerName()).isEqualTo("행복약국");
        assertThat(row.totalAmount()).isEqualTo(125_000L);
    }

    @Test
    @DisplayName("목록: 시작일이 종료일보다 늦으면 422 INVALID_DATE_RANGE")
    void listWithInvalidDateRange() {
        assertThatThrownBy(() -> deliveryService.getDeliveries(null, null,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("상세: 주문 라인을 품목으로 반환하고 출고·납품 일시는 아직 null")
    void detailReturnsOrderLines() {
        given(deliveryRepository.findDetailById(DELIVERY_ID)).willReturn(Optional.of(waitingDelivery()));

        DeliveryDetailResponse response = deliveryService.getDelivery(DELIVERY_ID);

        assertThat(response.items()).extracting(DeliveryDetailResponse.Line::lineAmount).containsExactly(50_000L, 75_000L);
        assertThat(response.totalAmount()).isEqualTo(125_000L);
        assertThat(response.shippedAt()).isNull();
        assertThat(response.deliveredAt()).isNull();
    }

    @Test
    @DisplayName("상세: 없는 납품이면 404 DELIVERY_NOT_FOUND")
    void detailOfUnknownDelivery() {
        given(deliveryRepository.findDetailById(DELIVERY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.getDelivery(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DELIVERY_NOT_FOUND);
    }

    @Test
    @DisplayName("출고: WAITING 납품을 잠근 뒤 SHIPPED로 바꾸고 shipped_at(초 단위) 기록")
    void shipWaitingDelivery() {
        Delivery delivery = waitingDelivery();
        given(deliveryRepository.findByIdForUpdate(DELIVERY_ID)).willReturn(Optional.of(delivery));

        DeliveryShipResponse response = deliveryService.ship(DELIVERY_ID);

        assertThat(response.status()).isEqualTo(DeliveryStatus.SHIPPED);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.shippedAt()).isNotNull();
        assertThat(response.shippedAt().getNano()).isZero();
    }

    @Test
    @DisplayName("출고: WAITING이 아니면 409 INVALID_DELIVERY_STATUS, 상태 유지")
    void shipAlreadyShippedDelivery() {
        Delivery delivery = shippedDelivery();
        given(deliveryRepository.findByIdForUpdate(DELIVERY_ID)).willReturn(Optional.of(delivery));

        assertThatThrownBy(() -> deliveryService.ship(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DELIVERY_STATUS);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPED);
    }

    @Test
    @DisplayName("출고: 없는 납품이면 404 DELIVERY_NOT_FOUND")
    void shipUnknownDelivery() {
        given(deliveryRepository.findByIdForUpdate(DELIVERY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> deliveryService.ship(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DELIVERY_NOT_FOUND);
    }

    @Test
    @DisplayName("납품 완료: DELIVERED로 바꾸고 주문 라인 스냅샷으로 매출 125,000·원가 87,500·마진 37,500·마진율 30.00, 매출일 = KST 날짜")
    void completeCreatesSaleFromOrderSnapshot() {
        Delivery delivery = shippedDelivery();
        given(deliveryRepository.findByIdForUpdate(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(saleRepository.existsByDeliveryId(DELIVERY_ID)).willReturn(false);
        given(saleRepository.saveAndFlush(any(Sale.class))).willAnswer(invocation -> {
            Sale sale = invocation.getArgument(0);
            ReflectionTestUtils.setField(sale, "saleId", 301L);
            return sale;
        });

        DeliveryCompleteResponse response = deliveryService.complete(DELIVERY_ID);

        assertThat(response.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(response.saleId()).isEqualTo(301L);
        assertThat(response.salesAmount()).isEqualTo(125_000L);
        assertThat(response.costAmount()).isEqualTo(87_500L);
        assertThat(response.marginAmount()).isEqualTo(37_500L);
        assertThat(response.deliveredAt()).isNotNull();

        ArgumentCaptor<Sale> sale = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository).saveAndFlush(sale.capture());
        assertThat(sale.getValue().getMarginRate()).isEqualTo(new BigDecimal("30.00"));
        assertThat(sale.getValue().getPartner().getPartnerId()).isEqualTo(PARTNER_ID);
        assertThat(sale.getValue().getOrder().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(sale.getValue().getSaleDate())
                .isEqualTo(LocalDate.ofInstant(response.deliveredAt(), ZoneId.of("Asia/Seoul")));
    }

    @Test
    @DisplayName("납품 완료: SHIPPED가 아니면(WAITING) 409 INVALID_DELIVERY_STATUS, 매출 생성 안 함")
    void completeWaitingDelivery() {
        given(deliveryRepository.findByIdForUpdate(DELIVERY_ID)).willReturn(Optional.of(waitingDelivery()));

        assertThatThrownBy(() -> deliveryService.complete(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DELIVERY_STATUS);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("납품 완료: 이미 매출이 있으면 409 SALE_ALREADY_RECORDED, 상태는 SHIPPED 유지")
    void completeWithExistingSale() {
        Delivery delivery = shippedDelivery();
        given(deliveryRepository.findByIdForUpdate(DELIVERY_ID)).willReturn(Optional.of(delivery));
        given(saleRepository.existsByDeliveryId(DELIVERY_ID)).willReturn(true);

        assertThatThrownBy(() -> deliveryService.complete(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_ALREADY_RECORDED);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SHIPPED);
        verify(saleRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("납품 완료: 매출 저장이 제약 위반으로 실패하면 500 SALE_CREATION_FAILED")
    void completeFailsWhenSaleInsertFails() {
        given(deliveryRepository.findByIdForUpdate(DELIVERY_ID)).willReturn(Optional.of(shippedDelivery()));
        given(saleRepository.existsByDeliveryId(DELIVERY_ID)).willReturn(false);
        given(saleRepository.saveAndFlush(any(Sale.class))).willThrow(new DataIntegrityViolationException("uk_sales_delivery"));

        assertThatThrownBy(() -> deliveryService.complete(DELIVERY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_CREATION_FAILED);
    }

    @Test
    @DisplayName("매출일: UTC 날짜가 아니라 KST 날짜 (UTC 15:00 이후 완료는 KST 다음 날)")
    void saleDateUsesKstDate() {
        assertThat(DeliveryService.saleDateOf(Instant.parse("2026-09-10T14:59:59Z"))).isEqualTo(LocalDate.of(2026, 9, 10));
        assertThat(DeliveryService.saleDateOf(Instant.parse("2026-09-10T15:00:00Z"))).isEqualTo(LocalDate.of(2026, 9, 11));
    }

    // 명세 9.2·9.4 예시: 타이레놀 10 × 5,000(원가 3,500) + 베아제 5 × 15,000(원가 10,500) = 125,000 / 원가 87,500
    private static Delivery waitingDelivery() {
        BusinessPartner customer = BusinessPartner.create(PartnerType.CUSTOMER, "행복약국", "123-45-67890",
                "02-1234-5678", "서울시 강남구", null);
        ReflectionTestUtils.setField(customer, "partnerId", PARTNER_ID);
        Order order = Order.create("ORD-20260910-0088", customer, 2L);
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        order.addItem(item(101L, "타이레놀정 500mg", 5_000, 3_500), 10);
        order.addItem(item(205L, "베아제정", 15_000, 10_500), 5);
        order.approve(Instant.parse("2026-09-10T05:30:00Z"));
        Delivery delivery = Delivery.create(order);
        ReflectionTestUtils.setField(delivery, "deliveryId", DELIVERY_ID);
        return delivery;
    }

    private static Delivery shippedDelivery() {
        Delivery delivery = waitingDelivery();
        delivery.ship(Instant.parse("2026-09-10T06:30:00Z"));
        return delivery;
    }

    // Item은 등록 API(6장)가 아직 없어 리플렉션으로 값을 채운다
    private static Item item(Long itemId, String itemName, long unitPrice, long unitCost) {
        try {
            var constructor = Item.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Item item = constructor.newInstance();
            ReflectionTestUtils.setField(item, "itemId", itemId);
            ReflectionTestUtils.setField(item, "itemCode", "IT-MED-NP-" + String.format("%04d", itemId));
            ReflectionTestUtils.setField(item, "itemName", itemName);
            ReflectionTestUtils.setField(item, "unitPrice", unitPrice);
            ReflectionTestUtils.setField(item, "unitCost", unitCost);
            ReflectionTestUtils.setField(item, "isActive", true);
            return item;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
