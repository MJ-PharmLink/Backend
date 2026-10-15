package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {

    // 같은 재고의 같은 로트번호 (UNIQUE(inventory_id, lot_number)). 상위 재고 행을 잠근 뒤 조회한다
    @Query("""
            select l from InventoryLot l
            where l.inventory.inventoryId = :inventoryId and l.lotNumber = :lotNumber
            """)
    Optional<InventoryLot> findByInventoryAndLotNumber(@Param("inventoryId") Long inventoryId,
                                                       @Param("lotNumber") String lotNumber);
}
