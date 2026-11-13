package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.ExpiringInventoryResponse;
import com.pharmlink.backend.domain.inventory.entity.Inventory;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
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
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryExpiringQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private InventoryLotRepository inventoryLotRepository;

    @InjectMocks
    private InventoryExpiringQueryService service;

    @Test
    @DisplayName("7.3 기본 조회: 90일 적용 및 LOT 응답 변환")
    void defaultSearch() {
        LocalDate today = LocalDate.now(KST);

        InventoryLot lot = mock(InventoryLot.class);
        Inventory inventory = mock(Inventory.class);
        Warehouse warehouse = mock(Warehouse.class);
        Item item = mock(Item.class);

        given(lot.getInventory()).willReturn(inventory);
        given(lot.getLotId()).willReturn(11L);
        given(lot.getLotNumber()).willReturn("LOT-TEST");
        given(lot.getExpiryDate()).willReturn(today.plusDays(90));
        given(lot.getQuantity()).willReturn(5);

        given(inventory.getInventoryId()).willReturn(21L);
        given(inventory.getWarehouse()).willReturn(warehouse);
        given(inventory.getItem()).willReturn(item);

        given(warehouse.getWarehouseId()).willReturn(31L);
        given(item.getItemId()).willReturn(41L);
        given(item.getItemCode()).willReturn("ITEM-TEST");
        given(item.getItemName()).willReturn("테스트 의약품");

        given(inventoryLotRepository.searchExpiringLots(
                isNull(), eq(today), eq(today.plusDays(90)),
                eq(true), eq(true), any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(lot)));

        Page<ExpiringInventoryResponse> result =
                service.getExpiringInventories(
                        null, null, null, null, null
                );

        assertThat(result.getContent()).hasSize(1);

        ExpiringInventoryResponse row = result.getContent().get(0);

        assertThat(row.lotId()).isEqualTo(11L);
        assertThat(row.inventoryId()).isEqualTo(21L);
        assertThat(row.warehouseId()).isEqualTo(31L);
        assertThat(row.itemId()).isEqualTo(41L);
        assertThat(row.itemCode()).isEqualTo("ITEM-TEST");
        assertThat(row.itemName()).isEqualTo("테스트 의약품");
        assertThat(row.daysUntilExpiry()).isEqualTo(90);
        assertThat(row.expiryStatus()).isEqualTo("EXPIRING_SOON");
        assertThat(row.quantity()).isEqualTo(5);

        verify(inventoryLotRepository, times(1))
                .searchExpiringLots(
                        isNull(), eq(today), eq(today.plusDays(90)),
                        eq(true), eq(true), any(Pageable.class)
                );
    }

    @Test
    @DisplayName("7.3 만료 필터: EXPIRED만 포함")
    void expiredOnly() {
        LocalDate today = LocalDate.now(KST);

        given(inventoryLotRepository.searchExpiringLots(
                eq(3L), eq(today), eq(today.plusDays(30)),
                eq(true), eq(false), any(Pageable.class)
        )).willReturn(Page.empty());

        Page<ExpiringInventoryResponse> result =
                service.getExpiringInventories(
                        30, "EXPIRED", 3L, 1, 20
                );

        assertThat(result).isEmpty();

        verify(inventoryLotRepository).searchExpiringLots(
                eq(3L), eq(today), eq(today.plusDays(30)),
                eq(true), eq(false), any(Pageable.class)
        );
    }

    @Test
    @DisplayName("7.3 임박 필터: days=0은 오늘까지")
    void expiringSoonToday() {
        LocalDate today = LocalDate.now(KST);

        given(inventoryLotRepository.searchExpiringLots(
                isNull(), eq(today), eq(today),
                eq(false), eq(true), any(Pageable.class)
        )).willReturn(Page.empty());

        service.getExpiringInventories(
                0, "EXPIRING_SOON", null, 1, 20
        );

        verify(inventoryLotRepository).searchExpiringLots(
                isNull(), eq(today), eq(today),
                eq(false), eq(true), any(Pageable.class)
        );
    }

    @Test
    @DisplayName("7.3 음수 days 입력은 VALIDATION_ERROR")
    void rejectNegativeDays() {
        assertThatThrownBy(() ->
                service.getExpiringInventories(
                        -1, null, null, 1, 20
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(inventoryLotRepository);
    }

    @Test
    @DisplayName("7.3 잘못된 상태 입력은 VALIDATION_ERROR")
    void rejectInvalidStatus() {
        assertThatThrownBy(() ->
                service.getExpiringInventories(
                        90, "NORMAL", null, 1, 20
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verifyNoInteractions(inventoryLotRepository);
    }

    @Test
    @DisplayName("7.3 페이지네이션: page=2, page_size 최대 100")
    void paginationLimit() {
        LocalDate today = LocalDate.now(KST);

        given(inventoryLotRepository.searchExpiringLots(
                isNull(), eq(today), eq(today.plusDays(90)),
                eq(true), eq(true), any(Pageable.class)
        )).willReturn(Page.empty());

        service.getExpiringInventories(
                null, null, null, 2, 500
        );

        var captor = org.mockito.ArgumentCaptor
                .forClass(Pageable.class);

        verify(inventoryLotRepository).searchExpiringLots(
                isNull(), eq(today), eq(today.plusDays(90)),
                eq(true), eq(true), captor.capture()
        );

        assertThat(captor.getValue().getPageNumber())
                .isEqualTo(1);
        assertThat(captor.getValue().getPageSize())
                .isEqualTo(100);
    }
}
