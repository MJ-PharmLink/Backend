
package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {

    // 기존 기능: 동일 LOT 번호 조회 및 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from InventoryLot l
            where l.inventory.inventoryId = :inventoryId
              and l.lotNumber = :lotNumber
            """)
    Optional<InventoryLot> findByInventoryAndLotNumber(
            @Param("inventoryId") Long inventoryId,
            @Param("lotNumber") String lotNumber
    );

    // 기존 기능: FEFO 출고 대상 LOT 조회 및 잠금
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from InventoryLot l
            where l.inventory.inventoryId = :inventoryId
              and l.quantity > 0
              and l.expiryDate >= :today
            order by l.expiryDate asc, l.lotId asc
            """)
    List<InventoryLot> findShippableLots(
            @Param("inventoryId") Long inventoryId,
            @Param("today") LocalDate today
    );

    // DAY 3: 특정 재고에 속한 전체 LOT 목록 조회
    // 유통기한이 빠른 순서로 반환
    List<InventoryLot> findByInventoryInventoryIdOrderByExpiryDateAscLotIdAsc(
            Long inventoryId
    );
}
