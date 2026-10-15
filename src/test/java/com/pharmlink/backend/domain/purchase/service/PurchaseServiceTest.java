package com.pharmlink.backend.domain.purchase.service;

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
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;
import com.pharmlink.backend.domain.partner.service.PartnerService;
import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateRequest;
import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateResponse;
import com.pharmlink.backend.domain.purchase.dto.PurchaseLineResponse;
import com.pharmlink.backend.domain.purchase.entity.Purchase;
import com.pharmlink.backend.domain.purchase.repository.PurchaseRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// PurchaseService 단위 테스트 (DB 없이 Repository·PartnerService를 Mock으로 대체)
// 재고 입고 로직(InventoryStockService)은 실제 객체를 써서 로트 생성·합산과 재고 이력까지 함께 검증한다
@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    private static final Long USER_ID = 3L;
    private static final Long PARTNER_ID = 15L;
    private static final Long WAREHOUSE_ID = 1L;
    private static final Long PURCHASE_ID = 202L;
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2026, 9, 10);

    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private PartnerService partnerService;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private InventoryLotRepository inventoryLotRepository;
    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    private PurchaseService purchaseService;
    private final AtomicLong nextLotId = new AtomicLong(13);

    @BeforeEach
    void setUp() {
        InventoryStockService inventoryStockService = new InventoryStockService(inventoryRepository,
                inventoryLotRepository, inventoryTransactionRepository);
        purchaseService = new PurchaseService(purchaseRepository, warehouseRepository, itemRepository, partnerService,
                inventoryStockService);
    }

    @Test
    @DisplayName("등록: 재고 행·로트가 없으면 새로 만들고, 원가 스냅샷·총액·재고 이력(IN, PURCHASE)을 남김")
    void createWithNewInventoryAndLot() {
        Warehouse warehouse = warehouse();
        Item tylenol = item(101L, 3500);
        givenValidHeader(warehouse, tylenol);
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 101L)).willReturn(Optional.empty());
        given(inventoryRepository.saveAndFlush(any(Inventory.class))).willAnswer(invocation -> {
            Inventory inventory = invocation.getArgument(0);
            ReflectionTestUtils.setField(inventory, "inventoryId", 7L);
            return inventory;
        });
        given(inventoryLotRepository.findByInventoryAndLotNumber(7L, "TL2706C")).willReturn(Optional.empty());
        givenLotSaveAssignsId();

        PurchaseCreateResponse response = purchaseService.createPurchase(USER_ID,
                request(WAREHOUSE_ID, line(101L, 100, "TL2706C", "2027-06-30")));

        assertThat(response.purchaseId()).isEqualTo(PURCHASE_ID);
        assertThat(response.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(response.totalAmount()).isEqualTo(350_000L);
        PurchaseLineResponse line = response.items().get(0);
        assertThat(line.unitCost()).isEqualTo(3500L);
        assertThat(line.lineAmount()).isEqualTo(350_000L);
        assertThat(line.lotId()).isEqualTo(13L);
        assertThat(line.expiryDate()).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(line.quantityAfter()).isEqualTo(100);

        InventoryTransaction transaction = savedTransactions(1).get(0);
        assertThat(transaction.getType()).isEqualTo(TransactionType.IN);
        assertThat(transaction.getReferenceType()).isEqualTo(ReferenceType.PURCHASE);
        assertThat(transaction.getReferenceId()).isEqualTo(PURCHASE_ID);
        assertThat(transaction.getLotId()).isEqualTo(13L);
        assertThat(transaction.getQuantity()).isEqualTo(100);
        assertThat(transaction.getQuantityAfter()).isEqualTo(100);
        assertThat(transaction.getCreatedBy()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("등록: 같은 로트번호·같은 유통기한이면 기존 로트에 합산, quantity_after는 기존 재고 + 입고 수량")
    void createMergesIntoExistingLot() {
        Warehouse warehouse = warehouse();
        Item beazet = item(205L, 10500);
        givenValidHeader(warehouse, beazet);
        Inventory inventory = inventory(warehouse, beazet, 4);
        InventoryLot lot = lot(inventory, 14L, "BA2703A", "2027-03-31", 4);
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 205L)).willReturn(Optional.of(inventory));
        given(inventoryLotRepository.findByInventoryAndLotNumber(8L, "BA2703A")).willReturn(Optional.of(lot));

        PurchaseCreateResponse response = purchaseService.createPurchase(USER_ID,
                request(WAREHOUSE_ID, line(205L, 50, "BA2703A", "2027-03-31")));

        assertThat(lot.getQuantity()).isEqualTo(54);
        assertThat(inventory.getQuantity()).isEqualTo(54);
        assertThat(response.items().get(0).lotId()).isEqualTo(14L);
        assertThat(response.items().get(0).quantityAfter()).isEqualTo(54);
        assertThat(response.totalAmount()).isEqualTo(525_000L);
        verify(inventoryLotRepository, never()).save(any());
        verify(inventoryRepository, never()).saveAndFlush(any());
        assertThat(savedTransactions(1).get(0).getQuantityAfter()).isEqualTo(54);
    }

    @Test
    @DisplayName("등록: 같은 상품의 다른 로트 두 줄이면 로트는 각각, quantity_after는 라인 순서대로 누적")
    void createSameItemWithTwoLots() {
        Warehouse warehouse = warehouse();
        Item tylenol = item(101L, 3500);
        givenValidHeader(warehouse, tylenol);
        Inventory inventory = inventory(warehouse, tylenol, 70);
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 101L)).willReturn(Optional.of(inventory));
        given(inventoryLotRepository.findByInventoryAndLotNumber(any(), any())).willReturn(Optional.empty());
        givenLotSaveAssignsId();

        PurchaseCreateResponse response = purchaseService.createPurchase(USER_ID, request(WAREHOUSE_ID,
                line(101L, 100, "TL2706C", "2027-06-30"), line(101L, 30, "TL2709A", "2027-09-30")));

        assertThat(response.items()).extracting(PurchaseLineResponse::quantityAfter).containsExactly(170, 200);
        assertThat(response.items()).extracting(PurchaseLineResponse::lotId).containsExactly(13L, 14L);
        assertThat(response.totalAmount()).isEqualTo(130 * 3500L);
        assertThat(savedTransactions(2)).extracting(InventoryTransaction::getQuantityAfter).containsExactly(170, 200);
    }

    @Test
    @DisplayName("등록: warehouse_id를 생략하면 기본 창고(is_default = true)에 입고")
    void createWithoutWarehouseUsesDefault() {
        Warehouse warehouse = warehouse();
        Item tylenol = item(101L, 3500);
        given(partnerService.getActiveSupplier(PARTNER_ID)).willReturn(supplier());
        given(warehouseRepository.findDefault()).willReturn(Optional.of(warehouse));
        given(itemRepository.findAllById(anyCollection())).willReturn(List.of(tylenol));
        givenPurchaseSaveAssignsId();
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 101L))
                .willReturn(Optional.of(inventory(warehouse, tylenol, 0)));
        given(inventoryLotRepository.findByInventoryAndLotNumber(any(), any())).willReturn(Optional.empty());
        givenLotSaveAssignsId();

        PurchaseCreateResponse response = purchaseService.createPurchase(USER_ID,
                request(null, line(101L, 10, "TL2706C", "2027-06-30")));

        assertThat(response.warehouseId()).isEqualTo(WAREHOUSE_ID);
        verify(warehouseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("등록: 기존 로트와 유통기한이 다르면 422 INVALID_EXPIRY_DATE, 재고·이력 변경 없음")
    void createWithMismatchedExpiryOnExistingLot() {
        Warehouse warehouse = warehouse();
        Item beazet = item(205L, 10500);
        givenValidHeader(warehouse, beazet);
        Inventory inventory = inventory(warehouse, beazet, 4);
        InventoryLot lot = lot(inventory, 14L, "BA2703A", "2027-03-31", 4);
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 205L)).willReturn(Optional.of(inventory));
        given(inventoryLotRepository.findByInventoryAndLotNumber(8L, "BA2703A")).willReturn(Optional.of(lot));

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID,
                request(WAREHOUSE_ID, line(205L, 50, "BA2703A", "2027-04-30"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_EXPIRY_DATE);
        assertThat(lot.getQuantity()).isEqualTo(4);
        assertThat(inventory.getQuantity()).isEqualTo(4);
        verify(inventoryTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 유통기한이 매입일과 같거나 이전이면 422 INVALID_EXPIRY_DATE, 저장하지 않음")
    void createWithExpiryNotAfterPurchaseDate() {
        given(partnerService.getActiveSupplier(PARTNER_ID)).willReturn(supplier());
        given(warehouseRepository.findById(WAREHOUSE_ID)).willReturn(Optional.of(warehouse()));

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID,
                request(WAREHOUSE_ID, line(101L, 10, "TL2706C", PURCHASE_DATE.toString()))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_EXPIRY_DATE);
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 수량이 1 미만이면 422 INVALID_QUANTITY")
    void createWithZeroQuantity() {
        given(partnerService.getActiveSupplier(PARTNER_ID)).willReturn(supplier());
        given(warehouseRepository.findById(WAREHOUSE_ID)).willReturn(Optional.of(warehouse()));

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID,
                request(WAREHOUSE_ID, line(101L, 0, "TL2706C", "2027-06-30"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_QUANTITY);
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 같은 (상품, 로트번호)가 두 번 들어오면 422 INVALID_QUANTITY")
    void createWithDuplicateItemLot() {
        given(partnerService.getActiveSupplier(PARTNER_ID)).willReturn(supplier());
        given(warehouseRepository.findById(WAREHOUSE_ID)).willReturn(Optional.of(warehouse()));

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID, request(WAREHOUSE_ID,
                line(101L, 10, "TL2706C", "2027-06-30"), line(101L, 5, "TL2706C", "2027-06-30"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("등록: 없는 창고면 404 WAREHOUSE_NOT_FOUND")
    void createWithUnknownWarehouse() {
        given(partnerService.getActiveSupplier(PARTNER_ID)).willReturn(supplier());
        given(warehouseRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID,
                request(99L, line(101L, 10, "TL2706C", "2027-06-30"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("등록: 없는 상품이 있으면 404 ITEM_NOT_FOUND")
    void createWithUnknownItem() {
        given(partnerService.getActiveSupplier(PARTNER_ID)).willReturn(supplier());
        given(warehouseRepository.findById(WAREHOUSE_ID)).willReturn(Optional.of(warehouse()));
        given(itemRepository.findAllById(anyCollection())).willReturn(List.of(item(101L, 3500)));

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID, request(WAREHOUSE_ID,
                line(101L, 10, "TL2706C", "2027-06-30"), line(999L, 5, "XX0001", "2027-06-30"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_NOT_FOUND);
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 단종 상품이 포함되면 409 ITEM_INACTIVE")
    void createWithInactiveItem() {
        Item discontinued = item(101L, 3500);
        ReflectionTestUtils.setField(discontinued, "isActive", false);
        given(partnerService.getActiveSupplier(PARTNER_ID)).willReturn(supplier());
        given(warehouseRepository.findById(WAREHOUSE_ID)).willReturn(Optional.of(warehouse()));
        given(itemRepository.findAllById(anyCollection())).willReturn(List.of(discontinued));

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID,
                request(WAREHOUSE_ID, line(101L, 10, "TL2706C", "2027-06-30"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ITEM_INACTIVE);
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 공급처가 아니면 PartnerService의 422 INVALID_PARTNER_TYPE을 그대로 반환")
    void createWithCustomerPartner() {
        given(partnerService.getActiveSupplier(PARTNER_ID))
                .willThrow(new BusinessException(ErrorCode.INVALID_PARTNER_TYPE));

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID,
                request(WAREHOUSE_ID, line(101L, 10, "TL2706C", "2027-06-30"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PARTNER_TYPE);
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("등록: 재고 행 생성이 동시 요청과 충돌하면 500 INVENTORY_UPDATE_FAILED")
    void createFailsWhenInventoryInsertConflicts() {
        Warehouse warehouse = warehouse();
        Item tylenol = item(101L, 3500);
        givenValidHeader(warehouse, tylenol);
        given(inventoryRepository.findForUpdate(WAREHOUSE_ID, 101L)).willReturn(Optional.empty());
        given(inventoryRepository.saveAndFlush(any(Inventory.class)))
                .willThrow(new DataIntegrityViolationException("uk_inv_warehouse_item"));

        assertThatThrownBy(() -> purchaseService.createPurchase(USER_ID,
                request(WAREHOUSE_ID, line(101L, 10, "TL2706C", "2027-06-30"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVENTORY_UPDATE_FAILED);
        verify(inventoryTransactionRepository, never()).save(any());
    }

    private void givenValidHeader(Warehouse warehouse, Item... items) {
        given(partnerService.getActiveSupplier(PARTNER_ID)).willReturn(supplier());
        given(warehouseRepository.findById(WAREHOUSE_ID)).willReturn(Optional.of(warehouse));
        given(itemRepository.findAllById(anyCollection())).willReturn(List.of(items));
        givenPurchaseSaveAssignsId();
    }

    private void givenPurchaseSaveAssignsId() {
        given(purchaseRepository.save(any(Purchase.class))).willAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            ReflectionTestUtils.setField(purchase, "purchaseId", PURCHASE_ID);
            return purchase;
        });
    }

    private void givenLotSaveAssignsId() {
        given(inventoryLotRepository.save(any(InventoryLot.class))).willAnswer(invocation -> {
            InventoryLot lot = invocation.getArgument(0);
            ReflectionTestUtils.setField(lot, "lotId", nextLotId.getAndIncrement());
            return lot;
        });
    }

    private List<InventoryTransaction> savedTransactions(int count) {
        ArgumentCaptor<InventoryTransaction> captor = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactionRepository, times(count)).save(captor.capture());
        return captor.getAllValues();
    }

    private static PurchaseCreateRequest request(Long warehouseId, PurchaseCreateRequest.Line... lines) {
        return new PurchaseCreateRequest(PARTNER_ID, warehouseId, PURCHASE_DATE, List.of(lines));
    }

    private static PurchaseCreateRequest.Line line(Long itemId, int quantity, String lotNumber, String expiryDate) {
        return new PurchaseCreateRequest.Line(itemId, quantity, lotNumber, LocalDate.parse(expiryDate));
    }

    private static BusinessPartner supplier() {
        BusinessPartner partner = BusinessPartner.create(PartnerType.SUPPLIER, "대한제약", "220-81-00001",
                "02-9876-5432", "경기도 성남시", null);
        ReflectionTestUtils.setField(partner, "partnerId", PARTNER_ID);
        return partner;
    }

    private static Warehouse warehouse() {
        Warehouse warehouse = newInstance(Warehouse.class);
        ReflectionTestUtils.setField(warehouse, "warehouseId", WAREHOUSE_ID);
        ReflectionTestUtils.setField(warehouse, "name", "본사 창고");
        ReflectionTestUtils.setField(warehouse, "isDefault", true);
        return warehouse;
    }

    // Item은 등록 API(6장)가 아직 없어 리플렉션으로 값을 채운다
    private static Item item(Long itemId, long unitCost) {
        Item item = newInstance(Item.class);
        ReflectionTestUtils.setField(item, "itemId", itemId);
        ReflectionTestUtils.setField(item, "itemCode", "IT-MED-NP-" + String.format("%04d", itemId));
        ReflectionTestUtils.setField(item, "itemName", "상품" + itemId);
        ReflectionTestUtils.setField(item, "unitCost", unitCost);
        ReflectionTestUtils.setField(item, "isActive", true);
        return item;
    }

    private static Inventory inventory(Warehouse warehouse, Item item, int quantity) {
        Inventory inventory = Inventory.create(warehouse, item);
        ReflectionTestUtils.setField(inventory, "inventoryId", 8L);
        inventory.increase(quantity);
        return inventory;
    }

    private static InventoryLot lot(Inventory inventory, Long lotId, String lotNumber, String expiryDate, int quantity) {
        InventoryLot lot = InventoryLot.create(inventory, lotNumber, LocalDate.parse(expiryDate), null);
        ReflectionTestUtils.setField(lot, "lotId", lotId);
        lot.increase(quantity);
        return lot;
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
