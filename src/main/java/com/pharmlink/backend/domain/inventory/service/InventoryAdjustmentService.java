package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.InventoryAdjustmentRequest;
import com.pharmlink.backend.domain.inventory.dto.InventoryTransactionResponse;
import com.pharmlink.backend.domain.inventory.entity.*;
import com.pharmlink.backend.domain.inventory.repository.*;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryAdjustmentService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Set<String> ALLOWED_REASONS = Set.of(
            "DAMAGED",
            "EXPIRED_DISPOSAL",
            "COUNT_CORRECTION",
            "RETURN",
            "OTHER"
    );

    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryLotRepository inventoryLotRepository;
    private final InventoryTransactionRepository transactionRepository;

    @Transactional
    public InventoryTransactionResponse adjust(
            Long userId,
            InventoryAdjustmentRequest request
    ) {
        // 1. 필수 값 및 조정 유형 검증
        if (userId == null || request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (request.warehouseId() == null
                || request.itemId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (request.quantity() == null
                || request.quantity() < 1) {
            throw new BusinessException(ErrorCode.INVALID_QUANTITY);
        }

        String type = request.adjustmentType();

        if (!"INCREASE".equals(type) && !"DECREASE".equals(type)) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY);
        }

        if (!ALLOWED_REASONS.contains(request.reason())) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY);
        }

        if (request.memo() != null && request.memo().length() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        // 2. LOT 조건 확인
        if ("INCREASE".equals(type)) {
            validateIncreaseRequest(request);
        } else {
            validateDecreaseRequest(request);
        }

        // 3. 창고 및 상품 존재 여부
        Warehouse warehouse = warehouseRepository
                .findById(request.warehouseId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND));

        Item item = itemRepository
                .findById(request.itemId())
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.ITEM_NOT_FOUND));

        // 4. 재고 행 잠금
        Inventory inventory = inventoryRepository
                .findForUpdate(warehouse.getWarehouseId(), item.getItemId())
                .orElse(null);

        // 5. 재고 증가 또는 감소
        InventoryLot lot;
        TransactionType transactionType;

        if ("INCREASE".equals(type)) {

            if (inventory == null) {
                try {
                    inventory = inventoryRepository.saveAndFlush(
                            Inventory.create(warehouse, item)
                    );
                } catch (DataIntegrityViolationException e) {
                    throw new BusinessException(
                            ErrorCode.INVENTORY_UPDATE_FAILED
                    );
                }
            }

            lot = resolveIncreaseLot(inventory, request);

            // int 범위 초과 방지
            if ((long) inventory.getQuantity() + request.quantity()
                    > Integer.MAX_VALUE
                    || (long) lot.getQuantity() + request.quantity()
                    > Integer.MAX_VALUE) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY);
            }

            lot.increase(request.quantity());
            inventory.increase(request.quantity());

            transactionType = TransactionType.ADJUST_IN;

        } else {

            if (inventory == null) {
                throw new BusinessException(ErrorCode.LOT_NOT_FOUND);
            }

            lot = inventoryLotRepository.findById(request.lotId())
                    .orElseThrow(() ->
                            new BusinessException(ErrorCode.LOT_NOT_FOUND));

            // 요청한 창고·상품에 속한 LOT인지 확인
            if (!lot.getInventory().getInventoryId()
                    .equals(inventory.getInventoryId())) {
                throw new BusinessException(ErrorCode.LOT_NOT_FOUND);
            }

            if (lot.getQuantity() < request.quantity()
                    || inventory.getQuantity() < request.quantity()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
            }

            lot.decrease(request.quantity());
            inventory.decrease(request.quantity());

            transactionType = TransactionType.ADJUST_OUT;
        }

        // 6. 변경된 총재고 수량을 기준으로 이력 생성
        InventoryTransaction transaction =
                InventoryTransaction.adjustment(
                        transactionType,
                        lot,
                        request.quantity(),
                        request.reason(),
                        request.memo(),
                        userId
                );

        transactionRepository.saveAndFlush(transaction);

        // 7. 생성된 조정 이력 반환
        return InventoryTransactionResponse.from(transaction);
    }

    private void validateIncreaseRequest(
            InventoryAdjustmentRequest request
    ) {
        boolean hasLotId = request.lotId() != null;
        boolean hasLotNumber = request.lotNumber() != null;
        boolean hasExpiryDate = request.expiryDate() != null;

        if (hasLotId) {
            // 기존 LOT를 지정할 때 신규 LOT 정보 동시 사용 금지
            if (hasLotNumber || hasExpiryDate) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        } else {
            // 신규 LOT 지정 시 번호와 유통기한 모두 필수
            if (!hasLotNumber || !hasExpiryDate
                    || request.lotNumber().isBlank()
                    || request.lotNumber().length() > 50) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }

            if (request.expiryDate().isBefore(LocalDate.now(KST))) {
                throw new BusinessException(ErrorCode.INVALID_EXPIRY_DATE);
            }
        }
    }

    private void validateDecreaseRequest(
            InventoryAdjustmentRequest request
    ) {
        if (request.lotId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        // 감소는 기존 LOT ID로만 처리
        if (request.lotNumber() != null
                || request.expiryDate() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private InventoryLot resolveIncreaseLot(
            Inventory inventory,
            InventoryAdjustmentRequest request
    ) {
        InventoryLot lot;

        if (request.lotId() != null) {

            lot = inventoryLotRepository.findById(request.lotId())
                    .orElseThrow(() ->
                            new BusinessException(ErrorCode.LOT_NOT_FOUND));

            if (!lot.getInventory().getInventoryId()
                    .equals(inventory.getInventoryId())) {
                throw new BusinessException(ErrorCode.LOT_NOT_FOUND);
            }

            if (lot.getExpiryDate().isBefore(LocalDate.now(KST))) {
                throw new BusinessException(ErrorCode.INVALID_EXPIRY_DATE);
            }

            return lot;
        }

        // LOT 번호가 이미 존재하는 경우
        lot = inventoryLotRepository
                .findByInventoryAndLotNumber(
                        inventory.getInventoryId(),
                        request.lotNumber()
                )
                .orElse(null);

        if (lot != null) {
            if (!lot.hasExpiryDate(request.expiryDate())) {
                throw new BusinessException(ErrorCode.INVALID_EXPIRY_DATE);
            }
            return lot;
        }

        // 신규 LOT 생성
        lot = InventoryLot.create(
                inventory,
                request.lotNumber(),
                request.expiryDate(),
                Instant.now()
        );

        return inventoryLotRepository.saveAndFlush(lot);
    }
}
