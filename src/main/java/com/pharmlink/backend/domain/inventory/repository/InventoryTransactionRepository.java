package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.InventoryTransaction;
import com.pharmlink.backend.domain.inventory.entity.TransactionType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository
        extends JpaRepository<InventoryTransaction, Long> {

    // 전체 재고 이력 조회
    // 상품 ID, 창고 ID, 입출고 유형으로 필터링 가능
    @Query("""
            SELECT t
            FROM InventoryTransaction t
            WHERE (:itemId IS NULL OR t.itemId = :itemId)
              AND (:warehouseId IS NULL OR t.warehouseId = :warehouseId)
              AND (:type IS NULL OR t.type = :type)
            """)
    Page<InventoryTransaction> search(
            @Param("itemId") Long itemId,
            @Param("warehouseId") Long warehouseId,
            @Param("type") TransactionType type,
            Pageable pageable
    );
}
