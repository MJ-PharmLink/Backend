package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // 재고 증감 전 (창고, 상품) 재고 행을 잠근다 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select i from Inventory i
            where i.warehouse.warehouseId = :warehouseId and i.item.itemId = :itemId
            """)
    Optional<Inventory> findForUpdate(@Param("warehouseId") Long warehouseId, @Param("itemId") Long itemId);
}
