package com.pharmlink.backend.domain.item.repository;

import com.pharmlink.backend.domain.item.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ItemRepository extends JpaRepository<Item, Long>,
        JpaSpecificationExecutor<Item> {

    boolean existsByItemCode(String itemCode);
}

