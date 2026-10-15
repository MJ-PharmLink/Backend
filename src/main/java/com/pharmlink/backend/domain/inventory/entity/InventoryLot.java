package com.pharmlink.backend.domain.inventory.entity;

import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

// inventory_lots — 재고 로트 (유통기한 단위). 같은 재고에 같은 lot_number가 다시 들어오면
// 유통기한이 같을 때만 수량을 합산하고, 다르면 INVALID_EXPIRY_DATE (매입 10.2, 재고 조정 7.4 공통)
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inventory_lots",
        uniqueConstraints = @UniqueConstraint(name = "uk_lot_inventory_lot_number",
                columnNames = {"inventory_id", "lot_number"}),
        indexes = {
                @Index(name = "idx_lot_expiry", columnList = "expiry_date"),
                @Index(name = "idx_lot_inv_expiry", columnList = "inventory_id, expiry_date")
        })
public class InventoryLot extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long lotId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false, updatable = false)
    private Inventory inventory;

    // 로트번호(제조번호)
    @Column(nullable = false, length = 50, updatable = false)
    private String lotNumber;

    @Column(nullable = false, updatable = false)
    private LocalDate expiryDate;

    // 로트 잔여 수량, 0 이상
    @Column(nullable = false)
    private int quantity;

    // 최초 입고 일시
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    public static InventoryLot create(Inventory inventory, String lotNumber, LocalDate expiryDate, Instant receivedAt) {
        InventoryLot lot = new InventoryLot();
        lot.inventory = inventory;
        lot.lotNumber = lotNumber;
        lot.expiryDate = expiryDate;
        lot.quantity = 0;
        lot.receivedAt = receivedAt;
        return lot;
    }

    public boolean hasExpiryDate(LocalDate expiryDate) {
        return this.expiryDate.equals(expiryDate);
    }

    public void increase(int amount) {
        this.quantity += amount;
    }
}
