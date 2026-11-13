package com.pharmlink.backend.domain.order.repository;

import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // 주문 목록: 각 조건이 null이면 그 조건은 적용하지 않음. 기간은 created_at 기준 [from, to)
    @Query(value = """
            select o from Order o join fetch o.partner
            where (:status is null or o.status = :status)
              and (:partnerId is null or o.partner.partnerId = :partnerId)
              and (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
            """,
            countQuery = """
            select count(o) from Order o
            where (:status is null or o.status = :status)
              and (:partnerId is null or o.partner.partnerId = :partnerId)
              and (:from is null or o.createdAt >= :from)
              and (:to is null or o.createdAt < :to)
            """)
    Page<Order> search(@Param("status") OrderStatus status,
                       @Param("partnerId") Long partnerId,
                       @Param("from") Instant from,
                       @Param("to") Instant to,
                       Pageable pageable);

    // 상세: 거래처·라인·라인 상품까지 한 번에 조회
    @Query("""
            select distinct o from Order o
            join fetch o.partner
            left join fetch o.items oi
            left join fetch oi.item
            where o.orderId = :orderId
            """)
    Optional<Order> findDetailById(@Param("orderId") Long orderId);

    // 승인·취소처럼 상태를 바꾸는 처리는 행을 잠근 뒤 상태를 다시 확인한다 (SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderId = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    // 채번: 전체 주문 일련번호의 최댓값 (ORD-YYYYMMDD-NNNN에서 14번째 문자부터). 주문이 없으면 0
    @Query(value = "select coalesce(max(cast(substring(order_number, 14) as unsigned)), 0) from orders",
            nativeQuery = true)
    long findMaxSequence();

    // DB에 저장된 created_at(초 단위). 스칼라 조회라 영속성 컨텍스트가 아닌 DB 값을 읽는다
    @Query("select x.createdAt from Order x where x.orderId = :id")
    Optional<Instant> findCreatedAt(@Param("id") Long id);
}
