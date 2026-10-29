package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.WarehouseResponse;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.WarehouseRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @InjectMocks
    private WarehouseService warehouseService;

    @Test
    @DisplayName("창고 목록: 등록된 창고를 정상 조회한다")
    void getWarehousesSuccess() {
        Warehouse warehouse = warehouse(1L, "본사 창고", true);

        when(warehouseRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(warehouse));

        List<WarehouseResponse> result = warehouseService.getWarehouses();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).warehouseId()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("본사 창고");
        assertThat(result.get(0).isDefault()).isTrue();

        verify(warehouseRepository).findAll(
                Sort.by(Sort.Direction.ASC, "warehouseId")
        );
    }

    @Test
    @DisplayName("창고 목록: 창고가 없으면 빈 목록을 반환한다")
    void getWarehousesEmpty() {
        when(warehouseRepository.findAll(any(Sort.class)))
                .thenReturn(List.of());

        List<WarehouseResponse> result = warehouseService.getWarehouses();

        assertThat(result).isEmpty();
    }

    private Warehouse warehouse(Long id, String name, boolean isDefault) {
        try {
            var constructor = Warehouse.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            Warehouse warehouse = constructor.newInstance();

            ReflectionTestUtils.setField(warehouse, "warehouseId", id);
            ReflectionTestUtils.setField(warehouse, "name", name);
            ReflectionTestUtils.setField(warehouse, "location", "서울");
            ReflectionTestUtils.setField(warehouse, "isDefault", isDefault);

            return warehouse;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
