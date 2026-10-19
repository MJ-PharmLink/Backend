package com.pharmlink.backend.domain.delivery.repository;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    // 주문 상세(8.3)의 delivery_id. 납품이 아직 없으면(PENDING·CANCELLED) empty
    @Query("select d.deliveryId from Delivery d where d.order.orderId = :orderId")
    Optional<Long> findDeliveryIdByOrderId(@Param("orderId") Long orderId);

    // 납품 목록 (9.1): 각 조건이 null이면 그 조건은 적용하지 않음. 기간은 납품 생성일(created_at) 기준 [from, to)
    @Query(value = """
            select d from Delivery d join fetch d.order o join fetch o.partner
            where (:status is null or d.status = :status)
              and (:partnerId is null or o.partner.partnerId = :partnerId)
              and (:from is null or d.createdAt >= :from)
              and (:to is null or d.createdAt < :to)
            """,
            countQuery = """
            select count(d) from Delivery d
            where (:status is null or d.status = :status)
              and (:partnerId is null or d.order.partner.partnerId = :partnerId)
              and (:from is null or d.createdAt >= :from)
              and (:to is null or d.createdAt < :to)
            """)
    Page<Delivery> search(@Param("status") DeliveryStatus status,
                          @Param("partnerId") Long partnerId,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);

    // 상세 (9.2): 주문·거래처·주문 라인·상품까지 한 번에 조회
    @Query("""
            select distinct d from Delivery d
            join fetch d.order o
            join fetch o.partner
            left join fetch o.items oi
            left join fetch oi.item
            where d.deliveryId = :deliveryId
            """)
    Optional<Delivery> findDetailById(@Param("deliveryId") Long deliveryId);

    // 출고(9.3)·납품 완료(9.4)는 납품 행을 잠근 뒤 상태를 다시 확인한다 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.deliveryId = :deliveryId")
    Optional<Delivery> findByIdForUpdate(@Param("deliveryId") Long deliveryId);

    // 주문 승인(8.4)에서 납품 생성 시각을 승인 시각과 같게 맞춘다 (DB 설계서 3.13)
    @Modifying(flushAutomatically = true)
    @Query("update Delivery d set d.createdAt = :createdAt where d.deliveryId = :deliveryId")
    int updateCreatedAt(@Param("deliveryId") Long deliveryId, @Param("createdAt") Instant createdAt);
}
