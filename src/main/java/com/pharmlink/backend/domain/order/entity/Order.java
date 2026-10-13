package com.pharmlink.backend.domain.order.entity;

import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// orders — 고객사 주문. 라인(order_items)은 주문과 생명주기를 같이한다
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_orders_order_number", columnNames = "order_number"),
        indexes = {
                @Index(name = "idx_orders_status_created", columnList = "status, created_at DESC"),
                @Index(name = "idx_orders_partner_created", columnList = "partner_id, created_at DESC")
        })
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    // ORD-YYYYMMDD-NNNN (NNNN은 전체 일련번호, 날짜가 바뀌어도 리셋하지 않음)
    @Column(nullable = false, length = 20, updatable = false)
    private String orderNumber;

    // CUSTOMER 거래처만
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private BusinessPartner partner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    // Σ order_items.line_amount
    @Column(nullable = false)
    private long totalAmount;

    @Column(length = 200)
    private String cancelReason;

    private Instant approvedAt;

    private Instant cancelledAt;

    // 취소자 users.user_id
    private Long cancelledBy;

    // 등록자 users.user_id
    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public static Order create(String orderNumber, BusinessPartner partner, Long createdBy) {
        Order order = new Order();
        order.orderNumber = orderNumber;
        order.partner = partner;
        order.status = OrderStatus.PENDING;
        order.totalAmount = 0L;
        order.createdBy = createdBy;
        return order;
    }

    // 주문 라인 추가: 상품 마스터의 단가·원가를 이 시점 값으로 스냅샷하고 총액을 갱신한다
    public OrderItem addItem(Item item, int quantity) {
        OrderItem orderItem = OrderItem.create(this, item, quantity);
        items.add(orderItem);
        totalAmount += orderItem.getLineAmount();
        return orderItem;
    }

    public boolean isPending() {
        return status == OrderStatus.PENDING;
    }

    // 승인(8.4): 재고 차감·납품 생성은 서비스에서 같은 트랜잭션으로 처리
    public void approve(Instant approvedAt) {
        this.status = OrderStatus.APPROVED;
        this.approvedAt = approvedAt;
    }

    // 취소(8.5): PENDING 여부는 서비스에서 검사. 등록 시 재고를 차감하지 않았으므로 재고 복구 없음
    public void cancel(Long cancelledBy, String cancelReason, Instant cancelledAt) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = cancelReason;
        this.cancelledBy = cancelledBy;
        this.cancelledAt = cancelledAt;
    }
}
