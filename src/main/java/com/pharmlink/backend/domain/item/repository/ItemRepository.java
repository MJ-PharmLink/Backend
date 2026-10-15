package com.pharmlink.backend.domain.item.repository;

import com.pharmlink.backend.domain.item.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

    boolean existsByItemCode(String itemCode);
}
