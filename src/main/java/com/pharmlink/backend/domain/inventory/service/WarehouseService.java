package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.WarehouseResponse;
import com.pharmlink.backend.domain.inventory.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public List<WarehouseResponse> getWarehouses() {
        return warehouseRepository.findAll(
                Sort.by(Sort.Direction.ASC, "warehouseId")
        ).stream()
                .map(WarehouseResponse::from)
                .toList();
    }
}
