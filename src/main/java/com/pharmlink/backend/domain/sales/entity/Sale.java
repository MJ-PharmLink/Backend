package com.pharmlink.backend.domain.sales.entity;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.sales.service.MarginCalculator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// sales — 매출/마진. 납품 완료(9.4) 트랜잭션에서만 INSERT하고 수정·삭제하지 않는다
// order_id·partner_id는 클라이언트가 지정하지 않고 납품이 가리키는 주문에서 복사한다
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "sales",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sales_order", columnNames = "order_id"),
                @UniqueConstraint(name = "uk_sales_delivery", columnNames = "delivery_id")
        },
        indexes = {
                @Index(name = "idx_sales_date", columnList = "sale_date"),
                @Index(name = "idx_sales_partner_date", columnList = "partner_id, sale_date")
        })
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long saleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id", nullable = false, updatable = false)
    private Delivery delivery;

    // 집계 성능용 비정규화 (orders.partner_id 복사)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private BusinessPartner partner;

    // Σ(quantity × unit_price), 주문 라인 스냅샷 기준
    @Column(nullable = false, updatable = false)
    private long salesAmount;

    // Σ(quantity × unit_cost), 주문 라인 스냅샷 기준
    @Column(nullable = false, updatable = false)
    private long costAmount;

    // salesAmount − costAmount
    @Column(nullable = false, updatable = false)
    private long marginAmount;

    // marginAmount ÷ salesAmount × 100 (소수 2자리, 매출 0이면 0.00)
    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal marginRate;

    // 매출일 = delivered_at의 KST 날짜
    @Column(nullable = false, updatable = false)
    private LocalDate saleDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // 납품 완료 시 주문 라인의 단가·원가 스냅샷으로 매출·원가·마진을 계산한다
    public static Sale of(Delivery delivery, LocalDate saleDate) {
        Order order = delivery.getOrder();
        long salesAmount = 0;
        long costAmount = 0;
        for (var line : order.getItems()) {
            salesAmount += (long) line.getQuantity() * line.getUnitPrice();
            costAmount += (long) line.getQuantity() * line.getUnitCost();
        }
        Sale sale = new Sale();
        sale.order = order;
        sale.delivery = delivery;
        sale.partner = order.getPartner();
        sale.salesAmount = salesAmount;
        sale.costAmount = costAmount;
        sale.marginAmount = salesAmount - costAmount;
        sale.marginRate = MarginCalculator.marginRate(salesAmount, sale.marginAmount);
        sale.saleDate = saleDate;
        return sale;
    }
}
