package com.pharmlink.backend.domain.inventory.repository;

import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    // 기본 창고 (is_default = true, 1건만 존재)
    @Query("select w from Warehouse w where w.isDefault = true")
    Optional<Warehouse> findDefault();
}
