package com.pharmlink.backend.domain.item.service;

import com.pharmlink.backend.domain.item.dto.ItemCreateRequest;
import com.pharmlink.backend.domain.item.dto.ItemResponse;
import com.pharmlink.backend.domain.item.dto.ItemUpdateRequest;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.CategoryRepository;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.domain.partner.service.PartnerService;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final PartnerService partnerService;

    // 기존 내부 호출 및 테스트 호환성 유지
    public Page<ItemResponse> getItems(Integer page, Integer pageSize) {
        return getItems(page, pageSize, null, null, null, null);
    }

    // 상품 검색 및 필터 목록 조회
    public Page<ItemResponse> getItems(
            Integer page,
            Integer pageSize,
            String keyword,
            Long categoryId,
            Long supplierId,
            Boolean includeInactive
    ) {
        Pageable pageable = PageRequestFactory.of(
                page,
                pageSize,
                Sort.by(Sort.Direction.DESC, "itemId")
        );

        String normalizedKeyword =
                keyword == null ? null : keyword.trim().toLowerCase();

        org.springframework.data.jpa.domain.Specification<Item> specification =
                (root, query, cb) -> cb.conjunction();

        if (normalizedKeyword != null && !normalizedKeyword.isEmpty()) {
            String escaped = normalizedKeyword
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            String pattern = "%" + escaped + "%";

            specification = specification.and((root, query, cb) ->
                    cb.or(
                            cb.like(cb.lower(root.get("itemCode")), pattern, '\\'),
                            cb.like(cb.lower(root.get("itemName")), pattern, '\\')
                    )
            );
        }

        if (categoryId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("categoryId"), categoryId)
            );
        }

        if (supplierId != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("supplierId"), supplierId)
            );
        }

        // 기존 API 호환: 미지정 시 단종 상품도 포함
        if (Boolean.FALSE.equals(includeInactive)) {
            specification = specification.and((root, query, cb) ->
                    cb.isTrue(root.get("isActive"))
            );
        }

        return itemRepository.findAll(specification, pageable)
                .map(ItemResponse::from);
    }

    // 상품 상세 조회
    public ItemResponse getItem(Long itemId) {
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.ITEM_NOT_FOUND)
                );

        return ItemResponse.from(item);
    }

    // 상품 등록
    @Transactional
    public ItemResponse createItem(ItemCreateRequest request) {

        String itemCode = request.itemCode().trim();
        String itemName = request.itemName().trim();
        String unit = request.unit().trim();

        if (itemRepository.existsByItemCode(itemCode)) {
            throw new BusinessException(ErrorCode.ITEM_ALREADY_EXISTS);
        }

        if (!categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        partnerService.getActiveSupplier(request.supplierId());

        Item item = Item.create(
                itemCode,
                itemName,
                request.categoryId(),
                request.spec(),
                unit,
                request.unitCost(),
                request.unitPrice(),
                request.safetyStock(),
                request.supplierId()
        );

        return ItemResponse.from(itemRepository.saveAndFlush(item));
    }

    // 상품 수정 (PATCH)
    @Transactional
    public ItemResponse updateItem(
            Long itemId,
            ItemUpdateRequest request
    ) {

        Item item = itemRepository.findById(itemId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.ITEM_NOT_FOUND)
                );

        // 카테고리 유효성 검사
        if (request.categoryId() != null
                && !categoryRepository.existsById(request.categoryId())) {
            throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        // 공급처 유효성 검사
        if (request.supplierId() != null) {
            partnerService.getActiveSupplier(request.supplierId());
        }

        // 상품명 공백 검사
        String itemName = request.itemName();

        if (itemName != null) {
            itemName = itemName.trim();

            if (itemName.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }

        // 단위 공백 검사
        String unit = request.unit();

        if (unit != null) {
            unit = unit.trim();

            if (unit.isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }

        // 요청에 포함된 필드만 수정
        item.update(
                itemName,
                request.categoryId(),
                request.spec(),
                unit,
                request.unitCost(),
                request.unitPrice(),
                request.safetyStock(),
                request.supplierId()
        );

        return ItemResponse.from(itemRepository.saveAndFlush(item));
    }

    // 상품 비활성화 (Soft Delete)
    @Transactional
    public void deactivateItem(Long itemId) {

        // 1. 상품 조회
        Item item = itemRepository.findById(itemId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.ITEM_NOT_FOUND)
                );

        // 2. 이미 비활성화된 상품이면 그대로 종료
        if (!item.isActive()) {
            return;
        }

        // 3. 상품 비활성화
        item.deactivate();

        // 4. DB에 변경 사항 저장
        itemRepository.saveAndFlush(item);
    }
}


