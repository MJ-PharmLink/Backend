package com.pharmlink.backend.domain.sales.repository;

import com.pharmlink.backend.domain.sales.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // 납품 완료(9.4) 전 중복 매출 확인 (UNIQUE(delivery_id))
    @Query("select count(s) > 0 from Sale s where s.delivery.deliveryId = :deliveryId")
    boolean existsByDeliveryId(@Param("deliveryId") Long deliveryId);
}
