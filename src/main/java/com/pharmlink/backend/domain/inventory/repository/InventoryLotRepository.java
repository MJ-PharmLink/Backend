
package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
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

    // 유통기한 만료 및 임박 LOT 조회
    // 만료 LOT는 days 제한 없이 조회하고, 임박 LOT에만 days를 적용한다.
    @Query(
            value = """
                    select l
                    from InventoryLot l
                    join fetch l.inventory i
                    join fetch i.item
                    where l.quantity > 0
                      and (:warehouseId is null
                           or i.warehouse.warehouseId = :warehouseId)
                      and (
                           (:includeExpired = true
                            and l.expiryDate < :today)
                           or
                           (:includeSoon = true
                            and l.expiryDate between :today and :expiryLimit)
                      )
                    order by l.expiryDate asc, l.lotId asc
                    """,
            countQuery = """
                    select count(l)
                    from InventoryLot l
                    where l.quantity > 0
                      and (:warehouseId is null
                           or l.inventory.warehouse.warehouseId = :warehouseId)
                      and (
                           (:includeExpired = true
                            and l.expiryDate < :today)
                           or
                           (:includeSoon = true
                            and l.expiryDate between :today and :expiryLimit)
                      )
                    """
    )
    Page<InventoryLot> searchExpiringLots(
            @Param("warehouseId") Long warehouseId,
            @Param("today") LocalDate today,
            @Param("expiryLimit") LocalDate expiryLimit,
            @Param("includeExpired") boolean includeExpired,
            @Param("includeSoon") boolean includeSoon,
            Pageable pageable
    );

}
