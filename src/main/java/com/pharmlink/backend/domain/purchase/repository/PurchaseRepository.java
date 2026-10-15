package com.pharmlink.backend.domain.purchase.repository;

import com.pharmlink.backend.domain.purchase.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
}
