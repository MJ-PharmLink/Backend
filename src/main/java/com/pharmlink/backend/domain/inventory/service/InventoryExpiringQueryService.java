package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.ExpiringInventoryResponse;
import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.repository.InventoryLotRepository;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryExpiringQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_DAYS = 90;

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("EXPIRED", "EXPIRING_SOON");

    private final InventoryLotRepository inventoryLotRepository;

    public Page<ExpiringInventoryResponse> getExpiringInventories(
            Integer days,
            String expiryStatus,
            Long warehouseId,
            Integer page,
            Integer pageSize
    ) {
        int resolvedDays = days == null ? DEFAULT_DAYS : days;

        if (resolvedDays < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        if (expiryStatus != null
                && !ALLOWED_STATUSES.contains(expiryStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        LocalDate today = LocalDate.now(KST);
        LocalDate expiryLimit;

        try {
            expiryLimit = today.plusDays(resolvedDays);
        } catch (DateTimeException | ArithmeticException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        boolean includeExpired = expiryStatus == null
                || "EXPIRED".equals(expiryStatus);

        boolean includeSoon = expiryStatus == null
                || "EXPIRING_SOON".equals(expiryStatus);

        Pageable pageable = PageRequestFactory.of(
                page,
                pageSize
        );

        return inventoryLotRepository.searchExpiringLots(
                warehouseId,
                today,
                expiryLimit,
                includeExpired,
                includeSoon,
                pageable
        ).map(lot -> toResponse(lot, today, resolvedDays));
    }

    private ExpiringInventoryResponse toResponse(
            InventoryLot lot,
            LocalDate today,
            int days
    ) {
        var inventory = lot.getInventory();
        var item = inventory.getItem();

        return new ExpiringInventoryResponse(
                lot.getLotId(),
                inventory.getInventoryId(),
                inventory.getWarehouse().getWarehouseId(),
                item.getItemId(),
                item.getItemCode(),
                item.getItemName(),
                lot.getLotNumber(),
                lot.getExpiryDate(),
                ChronoUnit.DAYS.between(today, lot.getExpiryDate()),
                lot.getQuantity(),
                InventoryStatusCalculator.expiryStatus(
                        lot.getExpiryDate(),
                        today,
                        days
                )
        );
    }
}
