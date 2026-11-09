package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.InventoryLotResponse;
import com.pharmlink.backend.domain.inventory.dto.InventoryResponse;
import com.pharmlink.backend.domain.inventory.entity.Inventory;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
import com.pharmlink.backend.domain.inventory.repository.InventoryRepository;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int EXPIRING_SOON_DAYS = 90;

    private final InventoryRepository inventoryRepository;
    private final InventoryLotRepository inventoryLotRepository;

    // 1. 전체 재고 목록 조회
    public Page<InventoryResponse> getInventories(
            Integer page,
            Integer pageSize
    ) {
        return getInventories(
                null, null, null, page, pageSize
        );
    }

    // 상품명·상품코드·창고·카테고리 조건 조회
    public Page<InventoryResponse> getInventories(
            String keyword,
            Long warehouseId,
            Long categoryId,
            Integer page,
            Integer pageSize
    ) {
        return getInventories(
                keyword, warehouseId, categoryId,
                null, null, page, pageSize
        );
    }

    public Page<InventoryResponse> getInventories(
            String keyword,
            Long warehouseId,
            Long categoryId,
            String stockStatus,
            String itemStatus,
            Integer page,
            Integer pageSize
    ) {
        return getInventories(
                keyword, warehouseId, categoryId,
                stockStatus, itemStatus, null, page, pageSize
        );
    }

    public Page<InventoryResponse> getInventories(
            String keyword,
            Long warehouseId,
            Long categoryId,
            String stockStatus,
            String itemStatus,
            String expiryStatus,
            Integer page,
            Integer pageSize
    ) {
        if (stockStatus != null && !java.util.Set.of(
                "NORMAL", "LOW_STOCK", "OUT_OF_STOCK"
        ).contains(stockStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (itemStatus != null && !java.util.Set.of(
                "ACTIVE", "DISCONTINUED"
        ).contains(itemStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (expiryStatus != null && !java.util.Set.of(
                "NORMAL", "EXPIRING_SOON", "EXPIRED"
        ).contains(expiryStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        LocalDate today = LocalDate.now(KST);
        LocalDate expiryLimit = today.plusDays(EXPIRING_SOON_DAYS);

        Pageable pageable = PageRequestFactory.of(
                page,
                pageSize,
                Sort.by(Sort.Direction.DESC, "inventoryId")
        );

        Specification<Inventory> specification =
                (root, query, cb) -> {
                    List<Predicate> predicates =
                            new java.util.ArrayList<>();

                    if (keyword != null && !keyword.isBlank()) {
                        String pattern = "%"
                                + keyword.trim()
                                        .toLowerCase(java.util.Locale.ROOT)
                                + "%";

                        predicates.add(cb.or(
                                cb.like(
                                        cb.lower(root.get("item")
                                                .get("itemName")),
                                        pattern
                                ),
                                cb.like(
                                        cb.lower(root.get("item")
                                                .get("itemCode")),
                                        pattern
                                )
                        ));
                    }

                    if (warehouseId != null) {
                        predicates.add(cb.equal(
                                root.get("warehouse")
                                        .get("warehouseId"),
                                warehouseId
                        ));
                    }

                    if (categoryId != null) {
                        predicates.add(cb.equal(
                                root.get("item")
                                        .get("categoryId"),
                                categoryId
                        ));
                    }

                    if (stockStatus != null) {
                        var quantity = root.<Integer>get("quantity");
                        var safetyStock = root.get("item")
                                .<Integer>get("safetyStock");

                        switch (stockStatus) {
                            case "OUT_OF_STOCK" ->
                                    predicates.add(cb.equal(quantity, 0));
                            case "LOW_STOCK" ->
                                    predicates.add(cb.and(
                                            cb.gt(quantity, 0),
                                            cb.lessThanOrEqualTo(
                                                    quantity, safetyStock
                                            )
                                    ));
                            case "NORMAL" ->
                                    predicates.add(cb.greaterThan(
                                            quantity, safetyStock
                                    ));
                        }
                    }

                    if (itemStatus != null) {
                        predicates.add(cb.equal(
                                root.get("item").get("isActive"),
                                "ACTIVE".equals(itemStatus)
                        ));
                    }

                    if (expiryStatus != null) {
                        var expiredQuery = query.subquery(Long.class);
                        var expiredLot = expiredQuery.from(
                                com.pharmlink.backend.domain.inventory.entity.InventoryLot.class
                        );

                        expiredQuery.select(expiredLot.get("lotId"))
                                .where(
                                        cb.equal(
                                                expiredLot.get("inventory")
                                                        .get("inventoryId"),
                                                root.get("inventoryId")
                                        ),
                                        cb.gt(expiredLot.get("quantity"), 0),
                                        cb.lessThan(
                                                expiredLot.<LocalDate>get("expiryDate"),
                                                today
                                        )
                                );

                        var soonQuery = query.subquery(Long.class);
                        var soonLot = soonQuery.from(
                                com.pharmlink.backend.domain.inventory.entity.InventoryLot.class
                        );

                        soonQuery.select(soonLot.get("lotId"))
                                .where(
                                        cb.equal(
                                                soonLot.get("inventory")
                                                        .get("inventoryId"),
                                                root.get("inventoryId")
                                        ),
                                        cb.gt(soonLot.get("quantity"), 0),
                                        cb.between(
                                                soonLot.<LocalDate>get("expiryDate"),
                                                today,
                                                expiryLimit
                                        )
                                );

                        var hasExpired = cb.exists(expiredQuery);
                        var hasSoon = cb.exists(soonQuery);
                        var hasStock = cb.gt(
                                root.<Integer>get("quantity"), 0
                        );

                        switch (expiryStatus) {
                            case "EXPIRED" ->
                                    predicates.add(cb.and(
                                            hasStock,
                                            hasExpired
                                    ));
                            case "EXPIRING_SOON" ->
                                    predicates.add(cb.and(
                                            hasStock,
                                            cb.not(hasExpired),
                                            hasSoon
                                    ));
                            case "NORMAL" ->
                                    predicates.add(cb.or(
                                            cb.not(hasStock),
                                            cb.and(
                                                    cb.not(hasExpired),
                                                    cb.not(hasSoon)
                                            )
                                    ));
                        }
                    }

                    return cb.and(
                            predicates.toArray(new Predicate[0])
                    );
                };

        return inventoryRepository
                .findAll(specification, pageable)
                .map(this::toResponse);
    }

    // 2. 재고 상세 조회
    public InventoryResponse getInventory(Long inventoryId) {

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVENTORY_NOT_FOUND)
                );

        return toResponse(inventory);
    }

    private InventoryResponse toResponse(Inventory inventory) {
        var item = inventory.getItem();
        int quantity = inventory.getQuantity();
        int safetyStock = item.getSafetyStock();

        var lots = inventoryLotRepository
                .findByInventoryInventoryIdOrderByExpiryDateAscLotIdAsc(
                        inventory.getInventoryId()
                );

        var summary = InventoryStatusCalculator.summarize(
                quantity,
                lots,
                LocalDate.now(KST),
                EXPIRING_SOON_DAYS
        );

        return new InventoryResponse(
                inventory.getInventoryId(),
                inventory.getWarehouse().getWarehouseId(),
                inventory.getWarehouse().getName(),
                item.getItemId(),
                item.getItemCode(),
                item.getItemName(),
                quantity,
                safetyStock,
                InventoryStatusCalculator.itemStatus(item.isActive()),
                summary.availableQuantity(),
                InventoryStatusCalculator.stockStatus(
                        quantity, safetyStock
                ),
                summary.lotCount(),
                summary.nearestExpiryDate(),
                summary.expiryStatus()
        );
    }

    // 3. 특정 재고의 LOT 목록 조회
    public List<InventoryLotResponse> getInventoryLots(Long inventoryId) {

        // 재고 존재 여부 확인
        if (!inventoryRepository.existsById(inventoryId)) {
            throw new BusinessException(ErrorCode.INVENTORY_NOT_FOUND);
        }

        // 유통기한이 빠른 순서로 LOT 목록 조회
        return inventoryLotRepository
                .findByInventoryInventoryIdOrderByExpiryDateAscLotIdAsc(inventoryId)
                .stream()
                .map(InventoryLotResponse::from)
                .toList();
    }
}
