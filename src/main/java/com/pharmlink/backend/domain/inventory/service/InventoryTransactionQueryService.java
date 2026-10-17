
package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.InventoryTransactionResponse;
import com.pharmlink.backend.domain.inventory.entity.TransactionType;
import com.pharmlink.backend.domain.inventory.repository.InventoryTransactionRepository;
import com.pharmlink.backend.global.common.PageRequestFactory;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryTransactionQueryService {

    private final InventoryTransactionRepository inventoryTransactionRepository;

    // 재고 입출고 이력 조회
    // 상품, 창고, 입출고 유형별 필터링 지원
    public Page<InventoryTransactionResponse> getTransactions(
            Long itemId,
            Long warehouseId,
            TransactionType type,
            Integer page,
            Integer pageSize
    ) {
        Pageable pageable = PageRequestFactory.of(
                page,
                pageSize,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("transactionId")
                )
        );

        return inventoryTransactionRepository.search(
                itemId,
                warehouseId,
                type,
                pageable
        ).map(InventoryTransactionResponse::from);
    }
}
