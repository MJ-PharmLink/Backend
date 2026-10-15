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

// 로트를 바꾸는 조회는 모두 SELECT ... FOR UPDATE로 읽는다.
// MySQL(REPEATABLE READ)의 일반 SELECT는 트랜잭션 초반 스냅샷을 읽어서, 재고 행 잠금을 기다린 뒤에도
// 앞선 트랜잭션이 바꾼 로트 수량을 못 보고 덮어쓸 수 있다(동시 입고·승인 시 수량 유실)
public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {

    // 같은 재고의 같은 로트번호 (UNIQUE(inventory_id, lot_number)). 상위 재고 행을 잠근 뒤 조회한다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from InventoryLot l
            where l.inventory.inventoryId = :inventoryId and l.lotNumber = :lotNumber
            """)
    Optional<InventoryLot> findByInventoryAndLotNumber(@Param("inventoryId") Long inventoryId,
                                                       @Param("lotNumber") String lotNumber);

    // FEFO 출고 대상: 잔여 수량이 있고 만료되지 않은(expiry_date >= 오늘) 로트를 유통기한 → lot_id 순으로
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from InventoryLot l
            where l.inventory.inventoryId = :inventoryId and l.quantity > 0 and l.expiryDate >= :today
            order by l.expiryDate asc, l.lotId asc
            """)
    List<InventoryLot> findShippableLots(@Param("inventoryId") Long inventoryId, @Param("today") LocalDate today);
}
