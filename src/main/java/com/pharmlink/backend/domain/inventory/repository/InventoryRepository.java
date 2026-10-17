package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // 기존 기능: 재고 변경 전 행 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from Inventory i
            where i.warehouse.warehouseId = :warehouseId
              and i.item.itemId = :itemId
            """)
    Optional<Inventory> findForUpdate(
            @Param("warehouseId") Long warehouseId,
            @Param("itemId") Long itemId
    );

    // DAY 3: 특정 창고의 특정 상품 재고 조회
    Optional<Inventory> findByWarehouseWarehouseIdAndItemItemId(
            Long warehouseId,
            Long itemId
    );
}
