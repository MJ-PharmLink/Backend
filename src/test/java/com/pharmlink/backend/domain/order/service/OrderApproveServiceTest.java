package com.pharmlink.backend.domain.order.service;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.delivery.repository.DeliveryRepository;
import com.pharmlink.backend.domain.inventory.dto.StockShortage;
import com.pharmlink.backend.domain.inventory.entity.Inventory;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.entity.InventoryTransaction;
import com.pharmlink.backend.domain.inventory.entity.ReferenceType;
import com.pharmlink.backend.domain.inventory.entity.TransactionType;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryTransactionRepository;
import com.pharmlink.backend.domain.inventory.repository.WarehouseRepository;
import com.pharmlink.backend.domain.inventory.service.InventoryStockService;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.domain.order.dto.OrderApproveResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 주문 승인(8.4) 단위 테스트. 재고 출고 로직(InventoryStockService)은 실제 객체를 써서
// FEFO 로트 차감, 재고 부족 판정, 출고 이력까지 함께 검증한다 (만료 로트 제외는 리포지토리 쿼리 조건이라 통합 테스트에서 확인)
@ExtendWith(MockitoExtension.class)
class OrderApproveServiceTest {

    private static final Long USER_ID = 2L;
    private static final Long ORDER_ID = 88L;
    private static final Long WAREHOUSE_ID = 1L;
    private static final Long DELIVERY_ID = 35L;

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private PartnerService partnerService;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryLotRepository inventoryLotRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    private OrderService orderService;
    private final Warehouse warehouse = warehouse();

    @BeforeEach
    void setUp() {
        InventoryStockService inventoryStockService = new InventoryStockService(inventoryRepository,
                inventoryLotRepository, inventoryTransactionRepository);
        orderService = new OrderService(orderRepository, itemRepository, deliveryRepository, partnerService,
                warehouseRepository, inventoryStockService,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));
    }

    @Test
    @DisplayName("승인: 유통기한이 빠른 로트부터 차감하고 로트별 출고 이력(OUT, ORDER)을 남긴 뒤 APPROVED·납품 WAITING 생성")
    void approveDeductsLotsInFefoOrder() {
        Item tylenol = item(101L, "타이레놀정 500mg");
        Item beazet = item(205L, "베아제정");
        Order order = pendingOrder(tylenol, 5, beazet, 2);
        Inventory tylenolStock = inventory(7L, tylenol, 13);
        InventoryLot soon = lot(tylenolStock, 13L, "2027-03-31", 3);
        InventoryLot later = lot(tylenolStock, 14L, "2027-06-30", 10);
        Inventory beazetStock = inventory(8L, beazet, 4);
        InventoryLot beazetLot = lot(beazetStock, 15L, "2027-03-31", 4);
        givenPendingOrder(order);
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 101L)).willReturn(Optional.of(tylenolStock));
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 205L)).willReturn(Optional.of(beazetStock));
        given(inventoryLotRepository.findShippableLots(eq(7L), any())).willReturn(List.of(soon, later));
        given(inventoryLotRepository.findShippableLots(eq(8L), any())).willReturn(List.of(beazetLot));
        givenDeliverySaveAssignsId();

        OrderApproveResponse response = orderService.approveOrder(ORDER_ID, USER_ID);

        assertThat(soon.getQuantity()).isZero();
        assertThat(later.getQuantity()).isEqualTo(8);
        assertThat(tylenolStock.getQuantity()).isEqualTo(8);
        assertThat(beazetLot.getQuantity()).isEqualTo(2);
        assertThat(beazetStock.getQuantity()).isEqualTo(2);

        List<InventoryTransaction> transactions = savedTransactions();
        assertThat(transactions).extracting(InventoryTransaction::getLotId).containsExactly(13L, 14L, 15L);
        assertThat(transactions).extracting(InventoryTransaction::getQuantity).containsExactly(3, 2, 2);
        assertThat(transactions).extracting(InventoryTransaction::getQuantityAfter).containsExactly(10, 8, 2);
        assertThat(transactions).allSatisfy(transaction -> {
            assertThat(transaction.getType()).isEqualTo(TransactionType.OUT);
            assertThat(transaction.getReferenceType()).isEqualTo(ReferenceType.ORDER);
            assertThat(transaction.getReferenceId()).isEqualTo(ORDER_ID);
            assertThat(transaction.getCreatedBy()).isEqualTo(USER_ID);
        });

        assertThat(order.getStatus()).isEqualTo(OrderStatus.APPROVED);
        assertThat(response.status()).isEqualTo(OrderStatus.APPROVED);
        assertThat(response.inventoryDeducted()).isTrue();
        assertThat(response.deliveryId()).isEqualTo(DELIVERY_ID);
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.WAITING);
        assertThat(response.approvedAt()).isNotNull().isEqualTo(order.getApprovedAt());
        assertThat(response.approvedAt().getNano()).isZero();
    }

    @Test
    @DisplayName("승인: 가용 재고가 부족한 상품이 있으면 409 INSUFFICIENT_STOCK, 부족 상품 전부를 details에 담고 아무것도 차감하지 않음")
    void approveFailsWhenStockIsInsufficient() {
        Item tylenol = item(101L, "타이레놀정 500mg");
        Item beazet = item(205L, "베아제정");
        Order order = pendingOrder(tylenol, 10, beazet, 1);
        Inventory tylenolStock = inventory(7L, tylenol, 6);
        InventoryLot tylenolLot = lot(tylenolStock, 13L, "2027-03-31", 6);
        givenPendingOrder(order);
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 101L)).willReturn(Optional.of(tylenolStock));
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 205L)).willReturn(Optional.empty());
        given(inventoryLotRepository.findShippableLots(eq(7L), any())).willReturn(List.of(tylenolLot));

        assertThatThrownBy(() -> orderService.approveOrder(ORDER_ID, USER_ID))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(e.getMessage()).isEqualTo("일부 상품의 재고가 부족합니다.");
                    assertThat(e.getDetails()).isEqualTo(List.of(
                            new StockShortage(101L, "타이레놀정 500mg", 10, 6),
                            new StockShortage(205L, "베아제정", 1, 0)));
                });
        assertThat(tylenolLot.getQuantity()).isEqualTo(6);
        assertThat(tylenolStock.getQuantity()).isEqualTo(6);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(inventoryTransactionRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
    }

    @Test
    @DisplayName("승인: 이미 승인된 주문은 409 ORDER_ALREADY_APPROVED")
    void approveAlreadyApprovedOrder() {
        Order order = pendingOrder(item(101L, "타이레놀정 500mg"), 5, null, 0);
        order.approve(Instant.now());
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.approveOrder(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ALREADY_APPROVED);
        verifyNoInteractions(inventoryRepository, deliveryRepository);
    }

    @Test
    @DisplayName("승인: 취소된 주문은 409 INVALID_ORDER_STATUS")
    void approveCancelledOrder() {
        Order order = pendingOrder(item(101L, "타이레놀정 500mg"), 5, null, 0);
        order.cancel(USER_ID, null, Instant.now());
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.approveOrder(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        verifyNoInteractions(inventoryRepository, deliveryRepository);
    }

    @Test
    @DisplayName("승인: 주문 이후 단종된 상품이 있으면 409 ITEM_INACTIVE, 재고를 건드리지 않음")
    void approveWithDiscontinuedItem() {
        Item discontinued = item(101L, "타이레놀정 500mg");
        Order order = pendingOrder(discontinued, 5, null, 0);
        ReflectionTestUtils.setField(discontinued, "isActive", false);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.approveOrder(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_INACTIVE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(inventoryRepository, deliveryRepository);
    }

    @Test
    @DisplayName("승인: 없는 주문이면 404 ORDER_NOT_FOUND")
    void approveUnknownOrder() {
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.approveOrder(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("승인: 재고 합계가 로트 합계보다 작아 음수가 되면 500 INVENTORY_UPDATE_FAILED")
    void approveFailsWhenInventoryTotalIsInconsistent() {
        Item tylenol = item(101L, "타이레놀정 500mg");
        Order order = pendingOrder(tylenol, 5, null, 0);
        Inventory tylenolStock = inventory(7L, tylenol, 2);
        InventoryLot tylenolLot = lot(tylenolStock, 13L, "2027-03-31", 5);
        givenPendingOrder(order);
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 101L)).willReturn(Optional.of(tylenolStock));
        given(inventoryLotRepository.findShippableLots(eq(7L), any())).willReturn(List.of(tylenolLot));

        assertThatThrownBy(() -> orderService.approveOrder(ORDER_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVENTORY_UPDATE_FAILED);
        verify(deliveryRepository, never()).save(any());
    }

    private void givenPendingOrder(Order order) {
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(warehouseRepository.findDefault()).willReturn(Optional.of(warehouse));
    }

    private void givenDeliverySaveAssignsId() {
        given(deliveryRepository.save(any(Delivery.class))).willAnswer(invocation -> {
            Delivery delivery = invocation.getArgument(0);
            ReflectionTestUtils.setField(delivery, "deliveryId", DELIVERY_ID);
            return delivery;
        });
    }

    private List<InventoryTransaction> savedTransactions() {
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactionRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues();
    }

    // 상품 2개까지 담은 PENDING 주문 (second가 null이면 1개)
    private static Order pendingOrder(Item first, int firstQuantity, Item second, int secondQuantity) {
        BusinessPartner customer = BusinessPartner.create(PartnerType.CUSTOMER, "행복약국", "123-45-67890",
                "02-1234-5678", "서울시 강남구", null);
        Order order = Order.create("ORD-20260910-0088", customer, USER_ID);
        ReflectionTestUtils.setField(order, "orderId", ORDER_ID);
        order.addItem(first, firstQuantity);
        if (second != null) {
            order.addItem(second, secondQuantity);
        }
        return order;
    }

    private Inventory inventory(Long inventoryId, Item item, int quantity) {
        Inventory inventory = Inventory.create(warehouse, item);
        ReflectionTestUtils.setField(inventory, "inventoryId", inventoryId);
        inventory.increase(quantity);
        return inventory;
    }

    private static InventoryLot lot(Inventory inventory, Long lotId, String expiryDate, int quantity) {
        InventoryLot lot = InventoryLot.create(inventory, "LOT" + lotId, LocalDate.parse(expiryDate), null);
        ReflectionTestUtils.setField(lot, "lotId", lotId);
        lot.increase(quantity);
        return lot;
    }

    private static Warehouse warehouse() {
        Warehouse warehouse = newInstance(Warehouse.class);
        ReflectionTestUtils.setField(warehouse, "warehouseId", WAREHOUSE_ID);
        ReflectionTestUtils.setField(warehouse, "isDefault", true);
        return warehouse;
    }

    // Item은 등록 API(6장)가 아직 없어 리플렉션으로 값을 채운다
    private static Item item(Long itemId, String itemName) {
        Item item = newInstance(Item.class);
        ReflectionTestUtils.setField(item, "itemId", itemId);
        ReflectionTestUtils.setField(item, "itemCode", "IT-MED-NP-" + String.format("%04d", itemId));
        ReflectionTestUtils.setField(item, "itemName", itemName);
        ReflectionTestUtils.setField(item, "unitPrice", 5000L);
        ReflectionTestUtils.setField(item, "unitCost", 3500L);
        ReflectionTestUtils.setField(item, "isActive", true);
        return item;
    }

    // 엔티티 기본 생성자는 protected라 다른 패키지의 테스트에서 직접 부를 수 없어 리플렉션으로 생성
    private static <T> T newInstance(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
