package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryStatusCalculatorTest {

    private final LocalDate today = LocalDate.of(2026, 10, 27);

    @Test
    void stockStatus() {
        assertThat(InventoryStatusCalculator.stockStatus(0, 20))
                .isEqualTo("OUT_OF_STOCK");
        assertThat(InventoryStatusCalculator.stockStatus(7, 20))
                .isEqualTo("LOW_STOCK");
        assertThat(InventoryStatusCalculator.stockStatus(20, 20))
                .isEqualTo("LOW_STOCK");
        assertThat(InventoryStatusCalculator.stockStatus(21, 20))
                .isEqualTo("NORMAL");
    }

    @Test
    void itemStatus() {
        assertThat(InventoryStatusCalculator.itemStatus(true))
                .isEqualTo("ACTIVE");
        assertThat(InventoryStatusCalculator.itemStatus(false))
                .isEqualTo("DISCONTINUED");
    }

    @Test
    void expiredLotsAreExcludedFromAvailableQuantity() {
        InventoryLot expired = lot(today.minusDays(1), 3);
        InventoryLot valid = lot(today.plusDays(30), 7);

        var result = InventoryStatusCalculator.summarize(
                10, List.of(expired, valid), today, 90
        );

        assertThat(result.availableQuantity()).isEqualTo(7);
        assertThat(result.lotCount()).isEqualTo(2);
        assertThat(result.nearestExpiryDate())
                .isEqualTo(today.plusDays(30));
        assertThat(result.expiryStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void expiryBoundary() {
        assertThat(InventoryStatusCalculator.expiryStatus(
                today.minusDays(1), today, 90
        )).isEqualTo("EXPIRED");

        assertThat(InventoryStatusCalculator.expiryStatus(
                today, today, 90
        )).isEqualTo("EXPIRING_SOON");

        assertThat(InventoryStatusCalculator.expiryStatus(
                today.plusDays(90), today, 90
        )).isEqualTo("EXPIRING_SOON");

        assertThat(InventoryStatusCalculator.expiryStatus(
                today.plusDays(91), today, 90
        )).isEqualTo("NORMAL");
    }

    @Test
    void emptyOrZeroQuantityLotsAreNormal() {
        var empty = InventoryStatusCalculator.summarize(
                0, List.of(), today, 90
        );

        assertThat(empty.availableQuantity()).isZero();
        assertThat(empty.expiryStatus()).isEqualTo("NORMAL");

        var depleted = InventoryStatusCalculator.summarize(
                0, List.of(lot(today.minusDays(3), 0)), today, 90
        );

        assertThat(depleted.availableQuantity()).isZero();
        assertThat(depleted.nearestExpiryDate()).isNull();
        assertThat(depleted.expiryStatus()).isEqualTo("NORMAL");
    }

    @Test
    void expiryDateTodayIsStillAvailable() {
        var result = InventoryStatusCalculator.summarize(
                5, List.of(lot(today, 5)), today, 90
        );

        assertThat(result.availableQuantity()).isEqualTo(5);
        assertThat(result.expiryStatus()).isEqualTo("EXPIRING_SOON");
    }


    @Test
    void onlyExpiredLotsHaveNoAvailableQuantity() {
        var result = InventoryStatusCalculator.summarize(
                10,
                List.of(
                        lot(today.minusDays(10), 4),
                        lot(today.minusDays(1), 6)
                ),
                today,
                90
        );

        assertThat(result.availableQuantity()).isZero();
        assertThat(result.lotCount()).isEqualTo(2);
        assertThat(result.nearestExpiryDate()).isNull();
        assertThat(result.expiryStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void onlyValidLotsUseNearestExpiryDate() {
        var result = InventoryStatusCalculator.summarize(
                10,
                List.of(
                        lot(today.plusDays(120), 4),
                        lot(today.plusDays(20), 6)
                ),
                today,
                90
        );

        assertThat(result.availableQuantity()).isEqualTo(10);
        assertThat(result.lotCount()).isEqualTo(2);
        assertThat(result.nearestExpiryDate())
                .isEqualTo(today.plusDays(20));
        assertThat(result.expiryStatus()).isEqualTo("EXPIRING_SOON");
    }

    private InventoryLot lot(LocalDate expiryDate, int quantity) {
        InventoryLot lot = InventoryLot.create(
                mock(com.pharmlink.backend.domain.inventory.entity.Inventory.class),
                "TEST-" + expiryDate,
                expiryDate,
                Instant.now()
        );
        if (quantity > 0) {
            lot.increase(quantity);
        }
        return lot;
    }
}
