package com.pharmlink.backend.domain.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

// inventory_transactions — 재고 이력 (INSERT ONLY, 수정·삭제 없음)
// reference_id는 다형 참조라 FK가 없다. 조정 이력만 reference_id가 null
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inventory_transactions",
        indexes = {
                @Index(name = "idx_tx_type", columnList = "type"),
                @Index(name = "idx_tx_item_created", columnList = "item_id, created_at DESC"),
                @Index(name = "idx_tx_wh_created", columnList = "warehouse_id, created_at DESC"),
                @Index(name = "idx_tx_ref", columnList = "reference_type, reference_id"),
                @Index(name = "idx_tx_created", columnList = "created_at DESC")
        })
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ReferenceType referenceType;

    @Column(updatable = false)
    private Long referenceId;

    @Column(nullable = false, updatable = false)
    private Long warehouseId;

    @Column(nullable = false, updatable = false)
    private Long itemId;

    @Column(nullable = false, updatable = false)
    private Long lotId;

    // 변동 수량 (항상 양수)
    @Column(nullable = false, updatable = false)
    private int quantity;

    // 이 이력 반영 직후의 inventories.quantity
    @Column(nullable = false, updatable = false)
    private int quantityAfter;

    // 조정 사유 (IN/OUT은 null)
    @Column(length = 30, updatable = false)
    private String reason;

    @Column(length = 200, updatable = false)
    private String memo;

    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // 매입 입고 이력 (IN, PURCHASE)
    public static InventoryTransaction purchaseIn(Long purchaseId, InventoryLot lot, int quantity, Long createdBy) {
        return of(TransactionType.IN, ReferenceType.PURCHASE, purchaseId, lot, quantity, createdBy);
    }

    private static InventoryTransaction of(TransactionType type, ReferenceType referenceType, Long referenceId,
                                           InventoryLot lot, int quantity, Long createdBy) {
        Inventory inventory = lot.getInventory();
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.type = type;
        transaction.referenceType = referenceType;
        transaction.referenceId = referenceId;
        transaction.warehouseId = inventory.getWarehouse().getWarehouseId();
        transaction.itemId = inventory.getItem().getItemId();
        transaction.lotId = lot.getLotId();
        transaction.quantity = quantity;
        transaction.quantityAfter = inventory.getQuantity();
        transaction.createdBy = createdBy;
        return transaction;
    }
}
