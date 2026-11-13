package com.pharmlink.backend.domain.inventory.controller;

import com.pharmlink.backend.domain.inventory.dto.WarehouseResponse;
import com.pharmlink.backend.domain.inventory.dto.WarehouseDetailResponse;
import com.pharmlink.backend.domain.inventory.service.WarehouseService;
import com.pharmlink.backend.global.response.ApiResponse;
import com.pharmlink.backend.global.security.AccessRole;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @PreAuthorize(AccessRole.ALL)
    @GetMapping
    public ApiResponse<List<WarehouseResponse>> getWarehouses() {
        return ApiResponse.success(warehouseService.getWarehouses());
    }

    @PreAuthorize(AccessRole.ADMIN_WAREHOUSE)
    @GetMapping("/{warehouseId}")
    public ApiResponse<WarehouseDetailResponse> getWarehouse(
            @PathVariable Long warehouseId
    ) {
        return ApiResponse.success(
                warehouseService.getWarehouse(warehouseId)
        );
    }

}
