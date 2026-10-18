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
import com.pharmlink.backend.domain.purchase.dto.PurchaseDetailResponse;
import com.pharmlink.backend.domain.purchase.dto.PurchaseResponse;
import com.pharmlink.backend.domain.purchase.entity.Purchase;
import com.pharmlink.backend.domain.purchase.repository.PurchaseRepository;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("purchaseDate"), Sort.Order.desc("purchaseId"));

    private final PurchaseRepository purchaseRepository;
    private final WarehouseRepository warehouseRepository;
    private final ItemRepository itemRepository;
    private final PartnerService partnerService;
    private final InventoryStockService inventoryStockService;

    // 매입 목록 (10.1, 최신 매입일순). 기간은 purchase_date 기준으로 시작일·종료일 모두 포함
    @Transactional(readOnly = true)
    public Page<PurchaseResponse> getPurchases(Long partnerId, Long warehouseId, LocalDate startDate,
                                               LocalDate endDate, Integer page, Integer pageSize) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        return purchaseRepository.search(partnerId, warehouseId, startDate, endDate,
                        PageRequestFactory.of(page, pageSize, LATEST_FIRST))
                .map(PurchaseResponse::from);
    }

    // 매입 상세 (10.3). 라인의 unit_cost는 매입 시점 스냅샷
    @Transactional(readOnly = true)
    public PurchaseDetailResponse getPurchase(Long purchaseId) {
        return purchaseRepository.findDetailById(purchaseId)
                .map(PurchaseDetailResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.PURCHASE_NOT_FOUND));
    }

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
        // 주문 승인(8.4)과 같은 item_id 순으로 재고 행을 잠가 동시 매입·승인 간 교착을 피한다
        // (같은 상품 라인끼리는 요청 순서 유지). 매입 라인과 응답은 요청 순서 그대로
        List<PurchaseCreateRequest.Line> lines = request.items();
        List<Integer> lockOrder = IntStream.range(0, lines.size()).boxed()
                .sorted(Comparator.comparing(i -> lines.get(i).itemId()))
                .toList();
        InventoryLot[] lots = new InventoryLot[lines.size()];
        Integer[] quantityAfter = new Integer[lines.size()];
        for (int i : lockOrder) {
            PurchaseCreateRequest.Line line = lines.get(i);
            lots[i] = inventoryStockService.receivePurchase(purchase.getPurchaseId(), warehouse,
                    items.get(line.itemId()), line.lotNumber().strip(), line.expiryDate(), line.quantity(), userId);
            quantityAfter[i] = lots[i].getInventory().getQuantity();
        }
        for (int i = 0; i < lines.size(); i++) {
            purchase.addItem(items.get(lines.get(i).itemId()), lots[i], lines.get(i).quantity());
        }
        // purchase_item_id를 응답에 바로 반영하기 위해 flush
        purchaseRepository.flush();
        return PurchaseCreateResponse.from(purchase, List.of(quantityAfter));
    }

    // warehouse_id를 생략하면 기본 창고(is_default = true). 없으면 404 WAREHOUSE_NOT_FOUND
    private Warehouse findWarehouse(Long warehouseId) {
        return (warehouseId == null ? warehouseRepository.findDefault() : warehouseRepository.findById(warehouseId))
                .orElseThrow(() -> new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND));
    }

    // 수량 1 미만 또는 같은 (상품, 로트번호) 중복 → 422 INVALID_QUANTITY
    // 로트번호는 DB(utf8mb4_unicode_ci)와 같은 기준으로 비교: 앞뒤 공백 제거, 대소문자 무시
    // 유통기한이 매입일 이후가 아니면 → 422 INVALID_EXPIRY_DATE
    private void validateLines(LocalDate purchaseDate, List<PurchaseCreateRequest.Line> lines) {
        Set<String> seen = new HashSet<>();
        for (PurchaseCreateRequest.Line line : lines) {
            String lotKey = line.lotNumber().strip().toUpperCase(Locale.ROOT);
            if (line.quantity() < 1 || !seen.add(line.itemId() + ":" + lotKey)) {
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
