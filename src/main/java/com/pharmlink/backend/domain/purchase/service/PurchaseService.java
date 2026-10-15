package com.pharmlink.backend.domain.purchase.service;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.WarehouseRepository;
import com.pharmlink.backend.domain.inventory.service.InventoryStockService;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.service.PartnerService;
import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateRequest;
import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateResponse;
import com.pharmlink.backend.domain.purchase.entity.Purchase;
import com.pharmlink.backend.domain.purchase.repository.PurchaseRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final PartnerService partnerService;
    private final InventoryStockService inventoryStockService;

    // 매입 등록 (10.2): 매입 헤더 → [라인별] 재고 행 잠금·생성 → 로트 생성/합산 → 매입 라인(lot_id, 원가 스냅샷)
    // → 재고 수량 증가 → 재고 이력(IN, PURCHASE). 어느 단계든 실패하면 전체 rollback
    @Transactional
    public PurchaseCreateResponse createPurchase(Long userId, PurchaseCreateRequest request) {
        BusinessPartner supplier = partnerService.getActiveSupplier(request.partnerId());
        Warehouse warehouse = findWarehouse(request.warehouseId());
        validateLines(request.purchaseDate(), request.items());
        Map<Long, Item> items = findActiveItems(request.items());

        // reference_id(purchase_id)를 재고 이력에 남기기 위해 헤더를 먼저 저장
        Purchase purchase = purchaseRepository.save(
                Purchase.create(supplier, warehouse, request.purchaseDate(), userId));
        List<Integer> quantityAfter = new ArrayList<>();
        for (PurchaseCreateRequest.Line line : request.items()) {
            Item item = items.get(line.itemId());
            InventoryLot lot = inventoryStockService.receivePurchase(purchase.getPurchaseId(), warehouse, item,
                    line.lotNumber(), line.expiryDate(), line.quantity(), userId);
            purchase.addItem(item, lot, line.quantity());
            quantityAfter.add(lot.getInventory().getQuantity());
        }
        // purchase_item_id를 응답에 바로 반영하기 위해 flush
        purchaseRepository.flush();
        return PurchaseCreateResponse.from(purchase, quantityAfter);
    }

    // warehouse_id를 생략하면 기본 창고(is_default = true). 없으면 404 WAREHOUSE_NOT_FOUND
    private Warehouse findWarehouse(Long warehouseId) {
        return (warehouseId == null ? warehouseRepository.findDefault() : warehouseRepository.findById(warehouseId))
                .orElseThrow(() -> new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }

    // 수량 1 미만 또는 같은 (상품, 로트번호) 중복 → 422 INVALID_QUANTITY
    // 유통기한이 매입일 이후가 아니면 → 422 INVALID_EXPIRY_DATE
    private void validateLines(LocalDate purchaseDate, List<PurchaseCreateRequest.Line> lines) {
        Set<String> seen = new HashSet<>();
        for (PurchaseCreateRequest.Line line : lines) {
            if (line.quantity() < 1 || !seen.add(line.itemId() + ":" + line.lotNumber())) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY);
            }
            if (!line.expiryDate().isAfter(purchaseDate)) {
                throw new BusinessException(ErrorCode.INVALID_EXPIRY_DATE);
            }
        }
    }

    // 없는 상품 404 ITEM_NOT_FOUND → 단종 상품 포함 409 ITEM_INACTIVE
    private Map<Long, Item> findActiveItems(List<PurchaseCreateRequest.Line> lines) {
        Set<Long> itemIds = lines.stream().map(PurchaseCreateRequest.Line::itemId).collect(Collectors.toSet());
        Map<Long, Item> items = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity()));
        if (items.size() != itemIds.size()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (items.values().stream().anyMatch(item -> !item.isActive())) {
            throw new BusinessException(ErrorCode.ITEM_INACTIVE);
        }
        return items;
    }
}
