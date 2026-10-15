package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {

    // 같은 재고의 같은 로트번호 (UNIQUE(inventory_id, lot_number)). 상위 재고 행을 잠근 뒤 조회한다
    @Query("""
            select l from InventoryLot l
            where l.inventory.inventoryId = :inventoryId and l.lotNumber = :lotNumber
            """)
    Optional<InventoryLot> findByInventoryAndLotNumber(@Param("inventoryId") Long inventoryId,
                                                       @Param("lotNumber") String lotNumber);

    // FEFO 출고 대상: 잔여 수량이 있고 만료되지 않은(expiry_date >= 오늘) 로트를 유통기한 → lot_id 순으로
    @Query("""
            select l from InventoryLot l
            where l.inventory.inventoryId = :inventoryId and l.quantity > 0 and l.expiryDate >= :today
            order by l.expiryDate asc, l.lotId asc
            """)
    List<InventoryLot> findShippableLots(@Param("inventoryId") Long inventoryId, @Param("today") LocalDate today);
}
