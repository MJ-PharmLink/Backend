package com.pharmlink.backend.domain.order.entity;

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

// order_items — 주문 라인. 납품 상세·매출 품목·납품서 PDF도 이 테이블을 재사용한다
// unit_price/unit_cost는 주문 시점 스냅샷이며 이후 상품 마스터가 바뀌어도 변경하지 않는다
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_oi_order_item", columnNames = {"order_id", "item_id"}),
        indexes = @Index(name = "idx_oi_item", columnList = "item_id"))
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false, updatable = false)
    private Item item;

    // 1 이상
    @Column(nullable = false)
    private int quantity;

    // 판매단가 스냅샷 (items.unit_price)
    @Column(nullable = false, updatable = false)
    private long unitPrice;

    // 원가 스냅샷 (items.unit_cost)
    @Column(nullable = false, updatable = false)
    private long unitCost;

    // quantity × unit_price
    @Column(nullable = false, updatable = false)
    private long lineAmount;

    static OrderItem create(Order order, Item item, int quantity) {
        OrderItem orderItem = new OrderItem();
        orderItem.order = order;
        orderItem.item = item;
        orderItem.quantity = quantity;
        orderItem.unitPrice = item.getUnitPrice();
        orderItem.unitCost = item.getUnitCost();
        orderItem.lineAmount = (long) quantity * item.getUnitPrice();
        return orderItem;
    }
}
