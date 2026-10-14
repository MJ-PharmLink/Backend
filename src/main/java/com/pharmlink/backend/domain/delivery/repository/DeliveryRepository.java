package com.pharmlink.backend.domain.delivery.repository;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    // 주문 상세(8.3)의 delivery_id. 납품이 아직 없으면(PENDING·CANCELLED) empty
    @Query("select d.deliveryId from Delivery d where d.order.orderId = :orderId")
    Optional<Long> findDeliveryIdByOrderId(@Param("orderId") Long orderId);
}
