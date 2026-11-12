package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.InventoryAdjustmentRequest;
import com.pharmlink.backend.domain.inventory.dto.InventoryTransactionResponse;
import com.pharmlink.backend.domain.inventory.entity.Inventory;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.entity.InventoryTransaction;
import com.pharmlink.backend.domain.inventory.entity.TransactionType;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryTransactionRepository;
import com.pharmlink.backend.domain.inventory.repository.WarehouseRepository;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryAdjustmentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long WAREHOUSE_ID = 1L;
    private static final Long ITEM_ID = 1L;
    private static final Long INVENTORY_ID = 10L;
    private static final Long LOT_ID = 100L;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryLotRepository inventoryLotRepository;

    @Mock
    private InventoryTransactionRepository transactionRepository;

    @InjectMocks
    private InventoryAdjustmentService adjustmentService;

    private Warehouse warehouse;
    private Item item;
    private Inventory inventory;
    private InventoryLot lot;

    @BeforeEach
    void setUp() {
        warehouse = mock(Warehouse.class);
        item = mock(Item.class);

        inventory = Inventory.create(warehouse, item);
        ReflectionTestUtils.setField(
                inventory, "inventoryId", INVENTORY_ID
        );
        inventory.increase(30);

        lot = InventoryLot.create(
                inventory,
                "TEST-LOT-001",
                LocalDate.now().plusYears(1),
                Instant.now()
        );
        ReflectionTestUtils.setField(lot, "lotId", LOT_ID);
        lot.increase(30);
    }

    // 창고, 상품, 재고가 모두 존재하는 상황
    private void mockExistingInventory() {
        when(warehouseRepository.findById(WAREHOUSE_ID))
                .thenReturn(Optional.of(warehouse));

        when(itemRepository.findById(ITEM_ID))
                .thenReturn(Optional.of(item));

        when(warehouse.getWarehouseId())
                .thenReturn(WAREHOUSE_ID);

        when(item.getItemId())
                .thenReturn(ITEM_ID);

        when(inventoryRepository.findForUpdate(
                WAREHOUSE_ID, ITEM_ID
        )).thenReturn(Optional.of(inventory));
    }

    private InventoryAdjustmentRequest request(
            Long lotId,
            String lotNumber,
            LocalDate expiryDate,
            String type,
            int quantity
    ) {
        return new InventoryAdjustmentRequest(
                WAREHOUSE_ID,
                ITEM_ID,
                lotId,
                lotNumber,
                expiryDate,
                type,
                quantity,
                "COUNT_CORRECTION",
                "재고 실사 보정"
        );
    }

    @Test
    @DisplayName("기존 LOT 증가: 총재고와 LOT 수량이 함께 증가한다")
    void increaseExistingLotSuccess() {
        mockExistingInventory();

        when(inventoryLotRepository.findById(LOT_ID))
                .thenReturn(Optional.of(lot));

        InventoryAdjustmentRequest request = request(
                LOT_ID, null, null, "INCREASE", 10
        );

        InventoryTransactionResponse result =
                adjustmentService.adjust(USER_ID, request);

        assertThat(inventory.getQuantity()).isEqualTo(40);
        assertThat(lot.getQuantity()).isEqualTo(40);
        assertThat(result.type())
                .isEqualTo(TransactionType.ADJUST_IN);
        assertThat(result.quantity()).isEqualTo(10);
        assertThat(result.quantityAfter()).isEqualTo(40);

        ArgumentCaptor<InventoryTransaction> captor =
                ArgumentCaptor.forClass(InventoryTransaction.class);

        verify(transactionRepository)
                .saveAndFlush(captor.capture());

        InventoryTransaction saved = captor.getValue();

        assertThat(saved.getCreatedBy()).isEqualTo(USER_ID);
        assertThat(saved.getReason())
                .isEqualTo("COUNT_CORRECTION");
        assertThat(saved.getMemo())
                .isEqualTo("재고 실사 보정");
        assertThat(saved.getReferenceId()).isNull();
    }

    @Test
    @DisplayName("기존 LOT 감소: 총재고와 LOT 수량이 함께 감소한다")
    void decreaseExistingLotSuccess() {
        mockExistingInventory();

        when(inventoryLotRepository.findById(LOT_ID))
                .thenReturn(Optional.of(lot));

        InventoryTransactionResponse result =
                adjustmentService.adjust(
                        USER_ID,
                        request(LOT_ID, null, null, "DECREASE", 5)
                );

        assertThat(inventory.getQuantity()).isEqualTo(25);
        assertThat(lot.getQuantity()).isEqualTo(25);
        assertThat(result.type())
                .isEqualTo(TransactionType.ADJUST_OUT);
        assertThat(result.quantity()).isEqualTo(5);
        assertThat(result.quantityAfter()).isEqualTo(25);

        verify(transactionRepository).saveAndFlush(
                any(InventoryTransaction.class)
        );
    }

    @Test
    @DisplayName("신규 LOT 증가: LOT가 생성되고 총재고가 증가한다")
    void increaseNewLotSuccess() {
        mockExistingInventory();

        String newLotNumber = "TEST-LOT-NEW";
        LocalDate expiryDate = LocalDate.now().plusYears(2);

        when(inventoryLotRepository.findByInventoryAndLotNumber(
                INVENTORY_ID, newLotNumber
        )).thenReturn(Optional.empty());

        when(inventoryLotRepository.saveAndFlush(
                any(InventoryLot.class)
        )).thenAnswer(invocation -> {
            InventoryLot saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(
                    saved, "lotId", 101L
            );
            return saved;
        });

        InventoryTransactionResponse result =
                adjustmentService.adjust(
                        USER_ID,
                        request(
                                null,
                                newLotNumber,
                                expiryDate,
                                "INCREASE",
                                7
                        )
                );

        assertThat(inventory.getQuantity()).isEqualTo(37);
        assertThat(result.type())
                .isEqualTo(TransactionType.ADJUST_IN);
        assertThat(result.lotId()).isEqualTo(101L);
        assertThat(result.quantityAfter()).isEqualTo(37);

        ArgumentCaptor<InventoryLot> captor =
                ArgumentCaptor.forClass(InventoryLot.class);

        verify(inventoryLotRepository)
                .saveAndFlush(captor.capture());

        assertThat(captor.getValue().getLotNumber())
                .isEqualTo(newLotNumber);
        assertThat(captor.getValue().getQuantity())
                .isEqualTo(7);
    }

    @Test
    @DisplayName("재고 부족: LOT 수량보다 많이 감소시킬 수 없다")
    void decreaseInsufficientStock() {
        mockExistingInventory();

        when(inventoryLotRepository.findById(LOT_ID))
                .thenReturn(Optional.of(lot));

        assertThatThrownBy(() ->
                adjustmentService.adjust(
                        USER_ID,
                        request(
                                LOT_ID, null, null,
                                "DECREASE", 50
                        )
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INSUFFICIENT_STOCK);

        assertThat(inventory.getQuantity()).isEqualTo(30);
        assertThat(lot.getQuantity()).isEqualTo(30);

        verify(transactionRepository, never())
                .saveAndFlush(any());
    }

    @Test
    @DisplayName("다른 재고의 LOT는 조정할 수 없다")
    void differentInventoryLotRejected() {
        mockExistingInventory();

        Inventory otherInventory = Inventory.create(
                warehouse, item
        );
        ReflectionTestUtils.setField(
                otherInventory, "inventoryId", 999L
        );

        InventoryLot otherLot = InventoryLot.create(
                otherInventory,
                "OTHER-LOT",
                LocalDate.now().plusYears(1),
                Instant.now()
        );
        ReflectionTestUtils.setField(
                otherLot, "lotId", 999L
        );

        when(inventoryLotRepository.findById(999L))
                .thenReturn(Optional.of(otherLot));

        assertThatThrownBy(() ->
                adjustmentService.adjust(
                        USER_ID,
                        request(
                                999L, null, null,
                                "DECREASE", 1
                        )
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOT_NOT_FOUND);

        verify(transactionRepository, never())
                .saveAndFlush(any());
    }

    @Test
    @DisplayName("지난 유통기한의 신규 LOT는 등록할 수 없다")
    void expiredNewLotRejected() {
        InventoryAdjustmentRequest request = request(
                null,
                "EXPIRED-LOT",
                LocalDate.now().minusDays(1),
                "INCREASE",
                5
        );

        assertThatThrownBy(() ->
                adjustmentService.adjust(USER_ID, request)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_EXPIRY_DATE);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    @DisplayName("수량이 0이면 INVALID_QUANTITY")
    void zeroQuantityRejected() {
        assertThatThrownBy(() ->
                adjustmentService.adjust(
                        USER_ID,
                        request(
                                LOT_ID, null, null,
                                "INCREASE", 0
                        )
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("음수 수량이면 INVALID_QUANTITY")
    void negativeQuantityRejected() {
        assertThatThrownBy(() ->
                adjustmentService.adjust(
                        USER_ID,
                        request(
                                LOT_ID, null, null,
                                "INCREASE", -3
                        )
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_QUANTITY);
    }

    @Test
    @DisplayName("감소 요청에 LOT ID가 없으면 VALIDATION_ERROR")
    void decreaseWithoutLotIdRejected() {
        assertThatThrownBy(() ->
                adjustmentService.adjust(
                        USER_ID,
                        request(
                                null, null, null,
                                "DECREASE", 5
                        )
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("증가 요청에 LOT ID와 신규 LOT 정보가 함께 있으면 오류")
    void increaseConflictingLotFieldsRejected() {
        assertThatThrownBy(() ->
                adjustmentService.adjust(
                        USER_ID,
                        request(
                                LOT_ID,
                                "NEW-LOT",
                                LocalDate.now().plusYears(1),
                                "INCREASE",
                                5
                        )
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
