package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {
}
