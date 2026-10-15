package com.pharmlink.backend.domain.inventory.entity;

import com.pharmlink.backend.domain.item.entity.Item;
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

// inventories — 재고 (창고 × 상품). quantity는 하위 로트 quantity의 합계
// 재고를 바꾸는 처리(매입·주문 승인·재고 조정)는 이 행을 SELECT ... FOR UPDATE로 잠근 뒤 갱신한다
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inventories",
        uniqueConstraints = @UniqueConstraint(name = "uk_inv_warehouse_item", columnNames = {"warehouse_id", "item_id"}),
        indexes = @Index(name = "idx_inv_item", columnList = "item_id"))
public class Inventory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long inventoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    // 0 이상
    @Column(nullable = false)
    private int quantity;

    public static Inventory create(Warehouse warehouse, Item item) {
        Inventory inventory = new Inventory();
        inventory.warehouse = warehouse;
        inventory.item = item;
        inventory.quantity = 0;
        return inventory;
    }

    public void increase(int amount) {
        this.quantity += amount;
    }

    public void decrease(int amount) {
        this.quantity -= amount;
    }
}
