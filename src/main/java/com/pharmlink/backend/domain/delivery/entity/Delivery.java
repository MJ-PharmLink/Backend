package com.pharmlink.backend.domain.delivery.entity;

import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

// deliveries — 납품 (주문과 1:1). 주문 승인(8.4) 시 WAITING으로 생성된다
// 재고는 승인 시점에 이미 차감되어 있으므로 납품 상태 전이는 재고를 바꾸지 않는다
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "deliveries",
        uniqueConstraints = @UniqueConstraint(name = "uk_del_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_del_status_created", columnList = "status, created_at DESC"))
public class Delivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long deliveryId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    private Instant shippedAt;

    private Instant deliveredAt;

    public static Delivery create(Order order) {
        Delivery delivery = new Delivery();
        delivery.order = order;
        delivery.status = DeliveryStatus.WAITING;
        return delivery;
    }

    // 출고(9.3): WAITING → SHIPPED
    public void ship(Instant shippedAt) {
        this.status = DeliveryStatus.SHIPPED;
        this.shippedAt = shippedAt;
    }

    // 납품 완료(9.4): SHIPPED → DELIVERED. 매출 생성은 서비스에서 같은 트랜잭션으로 처리
    public void complete(Instant deliveredAt) {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = deliveredAt;
    }
}
