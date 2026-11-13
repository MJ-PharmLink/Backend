package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.entity.Inventory;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryRepository;
import com.pharmlink.backend.domain.inventory.repository.WarehouseRepository;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseDetailServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryLotRepository inventoryLotRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    @DisplayName("창고 상세: 없는 창고는 WAREHOUSE_NOT_FOUND")
    void warehouseNotFound() {
        when(warehouseRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> warehouseService.getWarehouse(999L)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("창고 상세: 재고가 없으면 모든 요약 값은 0")
    void emptyWarehouse() {
        Warehouse warehouse = warehouse();

        when(warehouseRepository.findById(1L))
                .thenReturn(Optional.of(warehouse));

        when(inventoryRepository.findByWarehouseWarehouseId(1L))
                .thenReturn(List.of());

        var result = warehouseService.getWarehouse(1L);

        assertThat(result.warehouseId()).isEqualTo(1L);
        assertThat(result.summary().totalItemCount()).isZero();
        assertThat(result.summary().totalQuantity()).isZero();
        assertThat(result.summary().byStockStatus().normal()).isZero();
        assertThat(result.summary().byItemStatus().active()).isZero();
        assertThat(result.summary().byExpiryStatus().normal()).isZero();
    }

    @Test
    @DisplayName("창고 상세: 상품별 재고와 상태를 집계한다")
    void warehouseSummary() {
        Warehouse warehouse = warehouse();

        Item activeItem = item("MED001", 10, true);
        Item inactiveItem = item("MED002", 5, false);

        Inventory normal = inventory(1L, warehouse, activeItem, 20);
        Inventory empty = inventory(2L, warehouse, inactiveItem, 0);

        when(warehouseRepository.findById(1L))
                .thenReturn(Optional.of(warehouse));

        when(inventoryRepository.findByWarehouseWarehouseId(1L))
                .thenReturn(List.of(normal, empty));

        when(inventoryLotRepository
                .findByInventoryInventoryIdOrderByExpiryDateAscLotIdAsc(1L))
                .thenReturn(List.of());

        var summary = warehouseService.getWarehouse(1L).summary();

        assertThat(summary.totalItemCount()).isEqualTo(2);
        assertThat(summary.totalQuantity()).isEqualTo(20);

        assertThat(summary.byStockStatus().normal()).isEqualTo(1);
        assertThat(summary.byStockStatus().outOfStock()).isEqualTo(1);
        assertThat(summary.byStockStatus().lowStock()).isZero();

        assertThat(summary.byItemStatus().active()).isEqualTo(1);
        assertThat(summary.byItemStatus().discontinued()).isEqualTo(1);

        assertThat(summary.byExpiryStatus().normal()).isEqualTo(2);
    }

    private Warehouse warehouse() {
        try {
            var constructor = Warehouse.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            Warehouse warehouse = constructor.newInstance();

            ReflectionTestUtils.setField(warehouse, "warehouseId", 1L);
            ReflectionTestUtils.setField(warehouse, "name", "본사 창고");
            ReflectionTestUtils.setField(warehouse, "location", "서울시 송파구");
            ReflectionTestUtils.setField(warehouse, "isDefault", true);

            return warehouse;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Item item(String code, int safetyStock, boolean active) {
        Item item = Item.create(
                code, "테스트 상품", 1L, "500mg",
                "BOX", 1000L, 2000L, safetyStock, 1L
        );
        if (!active) {
            item.deactivate();
        }
        return item;
    }

    private Inventory inventory(
            Long id, Warehouse warehouse, Item item, int quantity
    ) {
        Inventory inventory = Inventory.create(warehouse, item);
        ReflectionTestUtils.setField(inventory, "inventoryId", id);
        inventory.increase(quantity);
        return inventory;
    }
}
