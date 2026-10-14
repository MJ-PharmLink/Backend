package com.pharmlink.backend.domain.order.service;

import com.pharmlink.backend.domain.delivery.repository.DeliveryRepository;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.domain.order.dto.OrderCancelRequest;
import com.pharmlink.backend.domain.order.dto.OrderCancelResponse;
import com.pharmlink.backend.domain.order.dto.OrderCreateRequest;
import com.pharmlink.backend.domain.order.dto.OrderCreateResponse;
import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderStatus;
import com.pharmlink.backend.domain.order.repository.OrderRepository;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;
import com.pharmlink.backend.domain.partner.service.PartnerService;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// OrderService 단위 테스트 (DB 없이 Repository·PartnerService를 Mock으로 대체)
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 2L;
    private static final Long PARTNER_ID = 10L;
    private static final Long ORDER_ID = 88L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private PartnerService partnerService;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // 실제 트랜잭션 없이 콜백만 실행되도록 TransactionManager를 Mock으로 둔다
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        orderService = new OrderService(orderRepository, itemRepository, deliveryRepository, partnerService,
                transactionTemplate);
    }

    @Test
    @DisplayName("등록: 단가·원가를 상품 마스터에서 스냅샷하고 총액을 합산, 주문번호는 MAX+1로 채번")
    void createSnapshotsPriceAndNumbersOrder() {
        given(partnerService.getActiveCustomer(PARTNER_ID)).willReturn(customer());
        given(itemRepository.findAllById(anyList())).willReturn(List.of(item(101L, 5000, 3500), item(205L, 15000, 10500)));
        given(orderRepository.findMaxSequence()).willReturn(87L);
        given(orderRepository.saveAndFlush(any(Order.class))).willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = orderService.createOrder(USER_ID, request(line(101L, 10), line(205L, 5)));

        assertThat(response.orderNumber()).matches("ORD-\\d{8}-0088");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.totalAmount()).isEqualTo(125000L);
        assertThat(response.createdBy()).isEqualTo(USER_ID);
        assertThat(response.items()).extracting("unitPrice", "lineAmount", "unitCost")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(5000L, 50000L, null),
                        org.assertj.core.groups.Tuple.tuple(15000L, 75000L, null));
    }

    @Test
    @DisplayName("등록: 주문번호 UNIQUE 충돌이 나면 최신 순번으로 1회 재시도")
    void createRetriesOnceOnDuplicateOrderNumber() {
        given(partnerService.getActiveCustomer(PARTNER_ID)).willReturn(customer());
        given(itemRepository.findAllById(anyList())).willReturn(List.of(item(101L, 5000, 3500)));
        given(orderRepository.findMaxSequence()).willReturn(87L, 88L);
        given(orderRepository.saveAndFlush(any(Order.class)))
                .willThrow(new DataIntegrityViolationException("uk_orders_order_number"))
                .willAnswer(invocation -> invocation.getArgument(0));

        OrderCreateResponse response = orderService.createOrder(USER_ID, request(line(101L, 1)));

        assertThat(response.orderNumber()).endsWith("-0089");
        verify(orderRepository, times(2)).saveAndFlush(any(Order.class));
    }

    @Test
    @DisplayName("등록: 수량이 1 미만이면 422 INVALID_QUANTITY, 저장하지 않음")
    void createWithZeroQuantity() {
        given(partnerService.getActiveCustomer(PARTNER_ID)).willReturn(customer());

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, request(line(101L, 0))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_QUANTITY);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("등록: 같은 상품이 두 번 들어오면 422 INVALID_QUANTITY")
    void createWithDuplicateItem() {
        given(partnerService.getActiveCustomer(PARTNER_ID)).willReturn(customer());

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, request(line(101L, 1), line(101L, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("등록: 없는 상품이 있으면 404 ITEM_NOT_FOUND")
    void createWithUnknownItem() {
        given(partnerService.getActiveCustomer(PARTNER_ID)).willReturn(customer());
        given(itemRepository.findAllById(anyList())).willReturn(List.of(item(101L, 5000, 3500)));

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, request(line(101L, 1), line(999L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("등록: 단종 상품이 포함되면 409 ITEM_INACTIVE")
    void createWithInactiveItem() {
        given(partnerService.getActiveCustomer(PARTNER_ID)).willReturn(customer());
        Item discontinued = item(101L, 5000, 3500);
        ReflectionTestUtils.setField(discontinued, "isActive", false);
        given(itemRepository.findAllById(anyList())).willReturn(List.of(discontinued));

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, request(line(101L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_INACTIVE);
    }

    @Test
    @DisplayName("목록: 시작일이 종료일보다 늦으면 422 INVALID_DATE_RANGE")
    void listWithInvalidDateRange() {
        assertThatThrownBy(() -> orderService.getOrders(null, null,
                LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 10), null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("취소: PENDING 주문은 CANCELLED로 바뀌고 사유·취소자·취소 일시가 저장됨")
    void cancelPendingOrder() {
        Order order = pendingOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.saveAndFlush(order)).willReturn(order);

        OrderCancelResponse response = orderService.cancelOrder(ORDER_ID, USER_ID,
                new OrderCancelRequest("거래처 요청으로 주문 철회"));

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.cancelReason()).isEqualTo("거래처 요청으로 주문 철회");
        assertThat(response.cancelledBy()).isEqualTo(USER_ID);
        assertThat(response.cancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("취소: 사유를 비우거나 본문을 생략하면 cancel_reason은 null")
    void cancelWithoutReason() {
        Order order = pendingOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(orderRepository.saveAndFlush(order)).willReturn(order);

        OrderCancelResponse response = orderService.cancelOrder(ORDER_ID, USER_ID, null);

        assertThat(response.cancelReason()).isNull();
    }

    @Test
    @DisplayName("취소: 이미 승인된 주문은 409 INVALID_ORDER_STATUS, 상태 유지")
    void cancelApprovedOrder() {
        Order order = pendingOrder();
        order.approve(java.time.Instant.now());
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("취소: 없는 주문이면 404 ORDER_NOT_FOUND")
    void cancelUnknownOrder() {
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID, USER_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    private static OrderCreateRequest request(OrderCreateRequest.Line... lines) {
        return new OrderCreateRequest(PARTNER_ID, List.of(lines));
    }

    private static OrderCreateRequest.Line line(Long itemId, int quantity) {
        return new OrderCreateRequest.Line(itemId, quantity);
    }

    private static BusinessPartner customer() {
        BusinessPartner partner = BusinessPartner.create(PartnerType.CUSTOMER, "행복약국", "123-45-67890",
                "02-1234-5678", "서울시 강남구", null);
        ReflectionTestUtils.setField(partner, "partnerId", PARTNER_ID);
        return partner;
    }

    // Item은 등록 API(6장)가 아직 없어 리플렉션으로 값을 채운다
    private static Item item(Long itemId, long unitPrice, long unitCost) {
        Item item = newItem();
        ReflectionTestUtils.setField(item, "itemId", itemId);
        ReflectionTestUtils.setField(item, "itemCode", "IT-MED-NP-" + String.format("%04d", itemId));
        ReflectionTestUtils.setField(item, "itemName", "상품" + itemId);
        ReflectionTestUtils.setField(item, "unitPrice", unitPrice);
        ReflectionTestUtils.setField(item, "unitCost", unitCost);
        ReflectionTestUtils.setField(item, "isActive", true);
        return item;
    }

    private static Order pendingOrder() {
        Order order = Order.create("ORD-20260910-0088", customer(), USER_ID);
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        return order;
    }

    // Item의 기본 생성자는 protected라 다른 패키지의 테스트에서 직접 부를 수 없어 리플렉션으로 생성
    private static Item newItem() {
        try {
            var constructor = Item.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
