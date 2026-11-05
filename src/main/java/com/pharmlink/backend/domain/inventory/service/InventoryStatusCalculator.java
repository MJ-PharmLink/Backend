package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class InventoryStatusCalculator {

    private InventoryStatusCalculator() {
    }

    public record LotSummary(
            int availableQuantity,
            int lotCount,
            LocalDate nearestExpiryDate,
            String expiryStatus
    ) {
    }

    public static String stockStatus(int quantity, int safetyStock) {
        if (quantity == 0) {
            return "OUT_OF_STOCK";
        }
        if (quantity <= safetyStock) {
            return "LOW_STOCK";
        }
        return "NORMAL";
    }

    public static String itemStatus(boolean active) {
        return active ? "ACTIVE" : "DISCONTINUED";
    }

    public static String expiryStatus(
            LocalDate nearestExpiryDate,
            LocalDate today,
            int days
    ) {
        if (nearestExpiryDate == null) {
            return "NORMAL";
        }
        if (nearestExpiryDate.isBefore(today)) {
            return "EXPIRED";
        }
        if (!nearestExpiryDate.isAfter(today.plusDays(days))) {
            return "EXPIRING_SOON";
        }
        return "NORMAL";
    }

    public static LotSummary summarize(
            int inventoryQuantity,
            List<InventoryLot> lots,
            LocalDate today,
            int days
    ) {
        Objects.requireNonNull(lots);
        Objects.requireNonNull(today);

        if (days < 0) {
            throw new IllegalArgumentException("days must be non-negative");
        }

        int availableQuantity = 0;
        LocalDate nearestExpiryDate = null;
        boolean hasExpiredLot = false;

        for (InventoryLot lot : lots) {
            if (lot.getQuantity() <= 0) {
                continue;
            }

            LocalDate expiryDate = lot.getExpiryDate();

            if (expiryDate.isBefore(today)) {
                hasExpiredLot = true;
                continue;
            }

            availableQuantity = Math.addExact(
                    availableQuantity,
                    lot.getQuantity()
            );

            if (nearestExpiryDate == null
                    || expiryDate.isBefore(nearestExpiryDate)) {
                nearestExpiryDate = expiryDate;
            }
        }

        String status;

        if (inventoryQuantity == 0) {
            status = "NORMAL";
        } else if (hasExpiredLot) {
            status = "EXPIRED";
        } else {
            status = expiryStatus(nearestExpiryDate, today, days);
        }

        return new LotSummary(
                availableQuantity,
                lots.size(),
                nearestExpiryDate,
                status
        );
    }

}
