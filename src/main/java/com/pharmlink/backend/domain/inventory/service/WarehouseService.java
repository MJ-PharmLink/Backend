package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.WarehouseResponse;
import com.pharmlink.backend.domain.inventory.dto.WarehouseDetailResponse;
import com.pharmlink.backend.domain.inventory.entity.Inventory;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.WarehouseRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 임시 기준: API 명세 7.1 확인 후 확정
    private static final int EXPIRING_SOON_DAYS = 90;

    private final WarehouseRepository warehouseRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryLotRepository inventoryLotRepository;

    public List<WarehouseResponse> getWarehouses() {
        return warehouseRepository.findAll(
                Sort.by(Sort.Direction.ASC, "warehouseId")
        ).stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    public WarehouseDetailResponse getWarehouse(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND));

        List<Inventory> inventories =
                inventoryRepository.findByWarehouseWarehouseId(warehouseId);

        long totalQuantity = 0;
        long stockNormal = 0;
        long lowStock = 0;
        long outOfStock = 0;
        long active = 0;
        long discontinued = 0;
        long expiryNormal = 0;
        long expiringSoon = 0;
        long expired = 0;

        LocalDate today = LocalDate.now(KST);

        for (Inventory inventory : inventories) {
            int quantity = inventory.getQuantity();
            int safetyStock = inventory.getItem().getSafetyStock();

            totalQuantity += quantity;

            String stockStatus =
                    InventoryStatusCalculator.stockStatus(
                            quantity, safetyStock);

            switch (stockStatus) {
                case "OUT_OF_STOCK" -> outOfStock++;
                case "LOW_STOCK" -> lowStock++;
                default -> stockNormal++;
            }

            String itemStatus =
                    InventoryStatusCalculator.itemStatus(
                            inventory.getItem().isActive());

            if ("ACTIVE".equals(itemStatus)) {
                active++;
            } else {
                discontinued++;
            }

            // 잔여 수량이 없는 재고는 NORMAL
            if (quantity == 0) {
                expiryNormal++;
                continue;
            }

            List<InventoryLot> lots = inventoryLotRepository
                    .findByInventoryInventoryIdOrderByExpiryDateAscLotIdAsc(
                            inventory.getInventoryId());

            var lotSummary = InventoryStatusCalculator.summarize(
                    quantity, lots, today, EXPIRING_SOON_DAYS);

            switch (lotSummary.expiryStatus()) {
                case "EXPIRED" -> expired++;
                case "EXPIRING_SOON" -> expiringSoon++;
                default -> expiryNormal++;
            }
        }

        var summary = new WarehouseDetailResponse.Summary(
                inventories.size(),
                totalQuantity,
                new WarehouseDetailResponse.StockStatus(
                        stockNormal, lowStock, outOfStock),
                new WarehouseDetailResponse.ItemStatus(
                        active, discontinued),
                new WarehouseDetailResponse.ExpiryStatus(
                        expiryNormal, expiringSoon, expired)
        );

        return WarehouseDetailResponse.from(warehouse, summary);
    }

}
