package com.pharmlink.backend.domain.item.entity;

import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// items — 상품(의약품) 마스터. 주문·매입 라인은 등록 시점의 unit_price/unit_cost를 스냅샷으로 복사한다
// 상품 등록·수정(6장)은 아직 없으므로 지금은 주문·재고에서 조회 용도로만 쓴다
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "items",
        uniqueConstraints = @UniqueConstraint(name = "uk_items_item_code", columnNames = "item_code"),
        indexes = {
                @Index(name = "idx_items_category", columnList = "category_id"),
                @Index(name = "idx_items_supplier", columnList = "supplier_id"),
                @Index(name = "idx_items_name", columnList = "item_name")
        })
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;

    // IT-MED-{CC}-NNNN 자동 채번 또는 사용자 입력 코드
    @Column(nullable = false, length = 20)
    private String itemCode;

    @Column(nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private Long categoryId;

    // 규격 (예: 500mg, 100정)
    @Column(length = 100)
    private String spec;

    @Column(nullable = false, length = 20)
    private String unit;

    // 원가 (매입 단가)
    @Column(nullable = false)
    private Long unitCost;

    // 판매단가
    @Column(nullable = false)
    private Long unitPrice;

    @Column(nullable = false)
    private int safetyStock;

    // 공급처 (business_partners, SUPPLIER)
    @Column(nullable = false)
    private Long supplierId;

    // false = 단종. 단종 상품은 주문 등록·승인 불가
    @Column(nullable = false)
    private boolean isActive;
}
