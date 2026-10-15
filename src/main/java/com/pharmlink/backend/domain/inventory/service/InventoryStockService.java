package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.StockRequest;
import com.pharmlink.backend.domain.inventory.dto.StockShortage;
import com.pharmlink.backend.domain.inventory.entity.Inventory;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.entity.InventoryTransaction;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryTransactionRepository;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// 재고 입출고 공통 처리. 공개 입고/출고 API는 없고 매입 등록(10.2)·주문 승인(8.4)·재고 조정(7.4) 트랜잭션 안에서만 호출한다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryStockService {

    // 로트 만료 판단(expiry_date >= 오늘)은 한국 날짜 기준
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String INSUFFICIENT_STOCK_MESSAGE = "일부 상품의 재고가 부족합니다.";

    private final InventoryRepository inventoryRepository;
    private final InventoryLotRepository inventoryLotRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    // 매입 입고: 재고 행 잠금(없으면 quantity=0으로 생성) → 로트 생성 또는 합산 → 재고 수량 증가 → 이력(IN, PURCHASE)
    // 반환한 로트의 inventory.quantity가 이 입고 직후의 재고 수량(quantity_after)이다
    public InventoryLot receivePurchase(Long purchaseId, Warehouse warehouse, Item item,
                                        String lotNumber, LocalDate expiryDate, int quantity, Long userId) {
        Inventory inventory = lockOrCreateInventory(warehouse, item);
        InventoryLot lot = addToLot(inventory, lotNumber, expiryDate, quantity);
        inventory.increase(quantity);
        inventoryTransactionRepository.save(InventoryTransaction.purchaseIn(purchaseId, lot, quantity, userId));
        return lot;
    }

    // 주문 출고 (8.4): 상품별 재고 행 잠금 → 가용 재고(만료 로트 제외) 확인 → 전부 충분할 때만 FEFO 순서로 로트 차감
    // → 로트별 이력(OUT, ORDER). 하나라도 부족하면 아무것도 차감하지 않고 409 INSUFFICIENT_STOCK (부족 상품 전부를 details에)
    public void shipOrder(Long orderId, Warehouse warehouse, List<StockRequest> requests, Long userId) {
        LocalDate today = LocalDate.now(KST);
        // 동시 승인 간 교착을 피하려고 항상 item_id 순으로 재고 행을 잠근다
        List<StockRequest> ordered = requests.stream()
                .sorted(Comparator.comparing(request -> request.item().getItemId()))
                .toList();

        List<Shipment> shipments = new ArrayList<>();
        List<StockShortage> shortages = new ArrayList<>();
        for (StockRequest request : ordered) {
            Item item = request.item();
            Inventory inventory = inventoryRepository.findForUpdate(warehouse.getWarehouseId(), item.getItemId())
                    .orElse(null);
            List<InventoryLot> lots = inventory == null
                    ? List.of()
                    : inventoryLotRepository.findShippableLots(inventory.getInventoryId(), today);
            int available = lots.stream().mapToInt(InventoryLot::getQuantity).sum();
            if (available < request.quantity()) {
                shortages.add(new StockShortage(item.getItemId(), item.getItemName(), request.quantity(), available));
            } else {
                shipments.add(new Shipment(inventory, lots, request.quantity()));
            }
        }
        if (!shortages.isEmpty()) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK, INSUFFICIENT_STOCK_MESSAGE, shortages);
        }

        for (Shipment shipment : shipments) {
            deductFefo(orderId, shipment, userId);
        }
    }

    // 유통기한이 빠른 로트부터 필요한 만큼 차감하고, 로트마다 차감 직후의 재고 수량으로 이력을 남긴다
    private void deductFefo(Long orderId, Shipment shipment, Long userId) {
        int remaining = shipment.quantity();
        for (InventoryLot lot : shipment.lots()) {
            if (remaining == 0) {
                break;
            }
            int taken = Math.min(remaining, lot.getQuantity());
            lot.decrease(taken);
            shipment.inventory().decrease(taken);
            if (shipment.inventory().getQuantity() < 0) {
                // 재고 합계와 로트 합계가 어긋난 경우: 음수 재고를 만들지 않고 전체 rollback
                log.error("재고 수량 불일치: inventoryId={}", shipment.inventory().getInventoryId());
                throw new BusinessException(ErrorCode.INVENTORY_UPDATE_FAILED);
            }
            inventoryTransactionRepository.save(InventoryTransaction.orderOut(orderId, lot, taken, userId));
            remaining -= taken;
        }
    }

    private record Shipment(Inventory inventory, List<InventoryLot> lots, int quantity) {
    }

    private Inventory lockOrCreateInventory(Warehouse warehouse, Item item) {
        return inventoryRepository.findForUpdate(warehouse.getWarehouseId(), item.getItemId())
                .orElseGet(() -> createInventory(warehouse, item));
    }

    // 동시 요청이 같은 (창고, 상품) 재고 행을 먼저 만들면 UNIQUE 충돌 → 전체 rollback, 500 INVENTORY_UPDATE_FAILED
    private Inventory createInventory(Warehouse warehouse, Item item) {
        try {
            return inventoryRepository.saveAndFlush(Inventory.create(warehouse, item));
        } catch (DataIntegrityViolationException e) {
            log.warn("재고 행 생성 충돌: warehouseId={}, itemId={}", warehouse.getWarehouseId(), item.getItemId());
            throw new BusinessException(ErrorCode.INVENTORY_UPDATE_FAILED);
        }
    }

    // 같은 lot_number 로트가 있으면 유통기한이 같을 때만 합산, 다르면 422 INVALID_EXPIRY_DATE
    private InventoryLot addToLot(Inventory inventory, String lotNumber, LocalDate expiryDate, int quantity) {
        InventoryLot lot = inventoryLotRepository.findByInventoryAndLotNumber(inventory.getInventoryId(), lotNumber)
                .orElse(null);
        if (lot == null) {
            lot = InventoryLot.create(inventory, lotNumber, expiryDate, Instant.now());
            lot.increase(quantity);
            return inventoryLotRepository.save(lot);
        }
        if (!lot.hasExpiryDate(expiryDate)) {
            throw new BusinessException(ErrorCode.INVALID_EXPIRY_DATE);
        }
        lot.increase(quantity);
        return lot;
    }
}
