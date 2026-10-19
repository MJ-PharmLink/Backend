package com.pharmlink.backend.domain.purchase.repository;

import com.pharmlink.backend.domain.purchase.entity.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.Instant;
import java.util.Optional;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    // 매입 목록: 각 조건이 null이면 그 조건은 적용하지 않음. 기간은 purchase_date 기준 [startDate, endDate] (양끝 포함)
    @Query(value = """
            select p from Purchase p join fetch p.partner
            where (:partnerId is null or p.partner.partnerId = :partnerId)
              and (:warehouseId is null or p.warehouse.warehouseId = :warehouseId)
              and (:startDate is null or p.purchaseDate >= :startDate)
              and (:endDate is null or p.purchaseDate <= :endDate)
            """,
            countQuery = """
            select count(p) from Purchase p
            where (:partnerId is null or p.partner.partnerId = :partnerId)
              and (:warehouseId is null or p.warehouse.warehouseId = :warehouseId)
              and (:startDate is null or p.purchaseDate >= :startDate)
              and (:endDate is null or p.purchaseDate <= :endDate)
            """)
    Page<Purchase> search(@Param("partnerId") Long partnerId,
                          @Param("warehouseId") Long warehouseId,
                          @Param("startDate") LocalDate startDate,
                          @Param("endDate") LocalDate endDate,
                          Pageable pageable);

    // 상세: 거래처·창고·라인·라인 상품·로트까지 한 번에 조회
    @Query("""
            select distinct p from Purchase p
            join fetch p.partner
            join fetch p.warehouse
            left join fetch p.items pi
            left join fetch pi.item
            left join fetch pi.lot
            where p.purchaseId = :purchaseId
            order by pi.purchaseItemId
            """)
    Optional<Purchase> findDetailById(@Param("purchaseId") Long purchaseId);

    // DB에 저장된 created_at(초 단위). 스칼라 조회라 영속성 컨텍스트가 아닌 DB 값을 읽는다
    @Query("select x.createdAt from Purchase x where x.purchaseId = :id")
    Optional<Instant> findCreatedAt(@Param("id") Long id);
}
