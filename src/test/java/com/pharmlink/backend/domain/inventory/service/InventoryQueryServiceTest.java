package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.InventoryLotResponse;
import com.pharmlink.backend.domain.inventory.dto.InventoryResponse;
import com.pharmlink.backend.domain.inventory.entity.Inventory;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryRepository;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryQueryServiceTest {

    private static final Long INVENTORY_ID = 1L;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryLotRepository inventoryLotRepository;

    @InjectMocks
    private InventoryQueryService inventoryQueryService;

    @Test
    @DisplayName("재고 목록 조회: 등록된 재고가 정상 반환된다")
    void getInventoriesSuccess() {

        Inventory inventory = createInventory();

        given(inventoryRepository.findAll(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(inventory)));

        Page<InventoryResponse> result =
                inventoryQueryService.getInventories(1, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).itemId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).quantity()).isEqualTo(50);
        assertThat(result.getContent().get(0).warehouseId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("재고 목록 조회: 재고가 없으면 빈 목록을 반환한다")
    void getInventoriesEmpty() {

        given(inventoryRepository.findAll(any(Pageable.class)))
                .willReturn(Page.empty());

        Page<InventoryResponse> result =
                inventoryQueryService.getInventories(1, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("재고 상세 조회: 정상적으로 재고 정보를 반환한다")
    void getInventorySuccess() {

        Inventory inventory = createInventory();

        given(inventoryRepository.findById(INVENTORY_ID))
                .willReturn(Optional.of(inventory));

        InventoryResponse result =
                inventoryQueryService.getInventory(INVENTORY_ID);

        assertThat(result.inventoryId()).isEqualTo(INVENTORY_ID);
        assertThat(result.itemCode()).isEqualTo("TEST_MED_0001");
        assertThat(result.quantity()).isEqualTo(50);
        assertThat(result.safetyStock()).isEqualTo(20);
    }

    @Test
    @DisplayName("재고 상세 조회: 존재하지 않는 재고면 INVENTORY_NOT_FOUND")
    void getInventoryNotFound() {

        given(inventoryRepository.findById(999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                inventoryQueryService.getInventory(999L)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND);
    }

    @Test
    @DisplayName("LOT 목록 조회: 정상적으로 LOT 정보를 반환한다")
    void getInventoryLotsSuccess() {

        Inventory inventory = createInventory();

        InventoryLot lot = InventoryLot.create(
                inventory,
                "TEST-LOT-001",
                LocalDate.of(2027, 12, 31),
                java.time.Instant.now()
        );

        lot.increase(50);

        given(inventoryRepository.existsById(INVENTORY_ID))
                .willReturn(true);

        given(inventoryLotRepository
                .findByInventoryInventoryIdOrderByExpiryDateAscLotIdAsc(
                        INVENTORY_ID
                ))
                .willReturn(List.of(lot));

        List<InventoryLotResponse> result =
                inventoryQueryService.getInventoryLots(INVENTORY_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).lotNumber()).isEqualTo("TEST-LOT-001");
        assertThat(result.get(0).quantity()).isEqualTo(50);
        assertThat(result.get(0).expiryDate())
                .isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @Test
    @DisplayName("LOT 목록 조회: 재고가 없으면 INVENTORY_NOT_FOUND")
    void getInventoryLotsNotFound() {

        given(inventoryRepository.existsById(999L))
                .willReturn(false);

        assertThatThrownBy(() ->
                inventoryQueryService.getInventoryLots(999L)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND);

        verify(inventoryLotRepository, never())
                .findByInventoryInventoryIdOrderByExpiryDateAscLotIdAsc(
                        999L
                );
    }

    private Inventory createInventory() {

        Warehouse warehouse = org.mockito.Mockito.mock(Warehouse.class);
        Item item = org.mockito.Mockito.mock(Item.class);

        Inventory inventory = Inventory.create(warehouse, item);
        inventory.increase(50);

        // Entity의 ID는 DB가 생성하므로 테스트에서만 설정
        org.springframework.test.util.ReflectionTestUtils.setField(
                inventory, "inventoryId", INVENTORY_ID
        );

        // Mockito의 strict stubbing을 고려하여 DTO 변환 시
        // 사용되는 값만 설정
        org.mockito.Mockito.lenient()
                .when(warehouse.getWarehouseId()).thenReturn(1L);
        org.mockito.Mockito.lenient()
                .when(warehouse.getName()).thenReturn("본사 창고");

        org.mockito.Mockito.lenient()
                .when(item.getItemId()).thenReturn(1L);
        org.mockito.Mockito.lenient()
                .when(item.getItemCode()).thenReturn("TEST_MED_0001");
        org.mockito.Mockito.lenient()
                .when(item.getItemName()).thenReturn("테스트 의약품");
        org.mockito.Mockito.lenient()
                .when(item.getSafetyStock()).thenReturn(20);

        return inventory;
    }
}
