package com.pharmlink.backend.domain.item.entity;

import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_code", nullable = false, length = 20, unique = true)
    private String itemCode;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "spec", length = 100)
    private String spec;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_cost", nullable = false)
    private Long unitCost;

    @Column(name = "unit_price", nullable = false)
    private Long unitPrice;

    @Column(name = "safety_stock", nullable = false)
    private Integer safetyStock;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    // 상품 등록
    public static Item create(
            String itemCode,
            String itemName,
            Long categoryId,
            String spec,
            String unit,
            Long unitCost,
            Long unitPrice,
            Integer safetyStock,
            Long supplierId
    ) {
        Item item = new Item();

        item.itemCode = itemCode;
        item.itemName = itemName;
        item.categoryId = categoryId;
        item.spec = spec;
        item.unit = unit;
        item.unitCost = unitCost;
        item.unitPrice = unitPrice;
        item.safetyStock = safetyStock;
        item.supplierId = supplierId;
        item.isActive = true;

        return item;
    }

    // 상품 수정: 전달된 값만 변경
    public void update(
            String itemName,
            Long categoryId,
            String spec,
            String unit,
            Long unitCost,
            Long unitPrice,
            Integer safetyStock,
            Long supplierId
    ) {
        if (itemName != null) {
            this.itemName = itemName;
        }

        if (categoryId != null) {
            this.categoryId = categoryId;
        }

        if (spec != null) {
            this.spec = spec;
        }

        if (unit != null) {
            this.unit = unit;
        }

        if (unitCost != null) {
            this.unitCost = unitCost;
        }

        if (unitPrice != null) {
            this.unitPrice = unitPrice;
        }

        if (safetyStock != null) {
            this.safetyStock = safetyStock;
        }

        if (supplierId != null) {
            this.supplierId = supplierId;
        }
    }

    // 상품 비활성화 (Soft Delete)
    public void deactivate() {
        this.isActive = false;
    }
}


