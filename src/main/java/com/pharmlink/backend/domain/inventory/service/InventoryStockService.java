package com.pharmlink.backend.domain.inventory.service;

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

// 재고 입출고 공통 처리. 공개 입고/출고 API는 없고 매입 등록(10.2)·주문 승인(8.4)·재고 조정(7.4) 트랜잭션 안에서만 호출한다
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class InventoryStockService {

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
