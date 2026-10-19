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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryQueryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryLotRepository inventoryLotRepository;

    // 1. 전체 재고 목록 조회 (페이지네이션)
    public Page<InventoryResponse> getInventories(
            Integer page,
            Integer pageSize
    ) {
        Pageable pageable = PageRequestFactory.of(
                page,
                pageSize,
                Sort.by(Sort.Direction.DESC, "inventoryId")
        );

        return inventoryRepository.findAll(pageable)
                .map(InventoryResponse::from);
    }

    // 2. 재고 상세 조회
    public InventoryResponse getInventory(Long inventoryId) {

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() ->
                        new BusinessException(ErrorCode.INVENTORY_NOT_FOUND)
                );

        return InventoryResponse.from(inventory);
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
