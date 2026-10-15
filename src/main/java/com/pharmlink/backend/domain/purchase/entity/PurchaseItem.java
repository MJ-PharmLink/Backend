package com.pharmlink.backend.domain.purchase.entity;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.item.entity.Item;
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

// purchase_items — 매입 라인. lot_number·expiry_date는 연결된 로트(inventory_lots)에서 읽는다
// unit_cost는 매입 시점 스냅샷이며 이후 상품 마스터 원가가 바뀌어도 변경하지 않는다
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "purchase_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_pi_purchase_lot", columnNames = {"purchase_id", "lot_id"}),
        indexes = {
                @Index(name = "idx_pi_item", columnList = "item_id"),
                @Index(name = "idx_pi_lot", columnList = "lot_id")
        })
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long purchaseItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    // 이 라인으로 생성되거나 수량이 합산된 로트
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false, updatable = false)
    private InventoryLot lot;

    // 1 이상
    @Column(nullable = false, updatable = false)
    private int quantity;

    // 원가 스냅샷 (items.unit_cost)
    @Column(nullable = false, updatable = false)
    private long unitCost;

    // quantity × unit_cost
    @Column(nullable = false, updatable = false)
    private long lineAmount;

    static PurchaseItem create(Purchase purchase, Item item, InventoryLot lot, int quantity) {
        PurchaseItem purchaseItem = new PurchaseItem();
        purchaseItem.purchase = purchase;
        purchaseItem.item = item;
        purchaseItem.lot = lot;
        purchaseItem.quantity = quantity;
        purchaseItem.unitCost = item.getUnitCost();
        purchaseItem.lineAmount = (long) quantity * item.getUnitCost();
        return purchaseItem;
    }
}
