package com.pharmlink.backend.domain.sales.repository;

import com.pharmlink.backend.domain.sales.dto.MarginRow;
import com.pharmlink.backend.domain.sales.entity.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // 납품 완료(9.4) 전 중복 매출 확인 (UNIQUE(delivery_id))
    @Query("select count(s) > 0 from Sale s where s.delivery.deliveryId = :deliveryId")
    boolean existsByDeliveryId(@Param("deliveryId") Long deliveryId);

    // 매출 목록 (11.1): 조건이 null이면 적용하지 않음. 기간은 sale_date(KST 업무 날짜) 기준 [startDate, endDate] 양끝 포함
    @Query(value = """
            select s from Sale s join fetch s.partner
            where (:partnerId is null or s.partner.partnerId = :partnerId)
              and (:startDate is null or s.saleDate >= :startDate)
              and (:endDate is null or s.saleDate <= :endDate)
            """,
            countQuery = """
            select count(s) from Sale s
            where (:partnerId is null or s.partner.partnerId = :partnerId)
              and (:startDate is null or s.saleDate >= :startDate)
              and (:endDate is null or s.saleDate <= :endDate)
            """)
    Page<Sale> search(@Param("partnerId") Long partnerId,
                      @Param("startDate") LocalDate startDate,
                      @Param("endDate") LocalDate endDate,
                      Pageable pageable);

    // 매출 상세 (11.2): 거래처·주문·주문 라인·상품까지 한 번에 조회 (품목별 금액은 주문 라인 스냅샷)
    @Query("""
            select distinct s from Sale s
            join fetch s.partner
            join fetch s.order o
            left join fetch o.items oi
            left join fetch oi.item
            where s.saleId = :saleId
            """)
    Optional<Sale> findDetailById(@Param("saleId") Long saleId);

    // 마진 집계 (11.3) — 모두 매출에 연결된 주문 라인 스냅샷 기준 (DB 설계서 3.16: sales JOIN orders JOIN order_items)
    // 상품 필터가 없으면 라인 합계 = sales 테이블의 매출액·원가 합계와 같다

    // 기간 전체 합계 (거래처·상품 필터 적용)
    @Query("""
            select new com.pharmlink.backend.domain.sales.dto.MarginRow(
                cast(null as Long), cast(null as String), cast(null as String),
                coalesce(sum(oi.lineAmount), 0L), coalesce(sum(oi.quantity * oi.unitCost), 0L))
            from Sale s join s.order o join o.items oi
            where s.saleDate >= :startDate and s.saleDate <= :endDate
              and (:partnerId is null or s.partner.partnerId = :partnerId)
              and (:itemId is null or oi.item.itemId = :itemId)
            """)
    MarginRow summarize(@Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate,
                        @Param("partnerId") Long partnerId,
                        @Param("itemId") Long itemId);

    // 거래처별 (group_by=PARTNER): 마진 큰 순, 같으면 거래처 ID 순 (명세에 정렬 규정 없음, 마진 분석 화면 기준)
    @Query("""
            select new com.pharmlink.backend.domain.sales.dto.MarginRow(
                p.partnerId, cast(null as String), p.name,
                sum(oi.lineAmount), sum(oi.quantity * oi.unitCost))
            from Sale s join s.partner p join s.order o join o.items oi
            where s.saleDate >= :startDate and s.saleDate <= :endDate
              and (:partnerId is null or p.partnerId = :partnerId)
              and (:itemId is null or oi.item.itemId = :itemId)
            group by p.partnerId, p.name
            order by sum(oi.lineAmount) - sum(oi.quantity * oi.unitCost) desc, p.partnerId asc
            """)
    List<MarginRow> summarizeByPartner(@Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate,
                                       @Param("partnerId") Long partnerId,
                                       @Param("itemId") Long itemId);

    // 상품별 (group_by=ITEM): 마진 큰 순, 같으면 상품 ID 순
    @Query("""
            select new com.pharmlink.backend.domain.sales.dto.MarginRow(
                i.itemId, i.itemCode, i.itemName,
                sum(oi.lineAmount), sum(oi.quantity * oi.unitCost))
            from Sale s join s.order o join o.items oi join oi.item i
            where s.saleDate >= :startDate and s.saleDate <= :endDate
              and (:partnerId is null or s.partner.partnerId = :partnerId)
              and (:itemId is null or i.itemId = :itemId)
            group by i.itemId, i.itemCode, i.itemName
            order by sum(oi.lineAmount) - sum(oi.quantity * oi.unitCost) desc, i.itemId asc
            """)
    List<MarginRow> summarizeByItem(@Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate,
                                    @Param("partnerId") Long partnerId,
                                    @Param("itemId") Long itemId);
}
