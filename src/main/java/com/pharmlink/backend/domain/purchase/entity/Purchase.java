package com.pharmlink.backend.domain.purchase.entity;

import com.pharmlink.backend.domain.inventory.entity.InventoryLot;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// purchases — 공급처 매입(입고). 수정·삭제 API 없음
// 등록 시 로트 생성·재고 증가·재고 이력(IN)까지 한 트랜잭션으로 처리한다 (10.2)
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "purchases",
        indexes = {
                @Index(name = "idx_pur_date", columnList = "purchase_date"),
                @Index(name = "idx_pur_partner_date", columnList = "partner_id, purchase_date DESC"),
                @Index(name = "idx_pur_wh_date", columnList = "warehouse_id, purchase_date DESC")
        })
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long purchaseId;

    // SUPPLIER 거래처만
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false, updatable = false)
    private BusinessPartner partner;

    // 입고 창고 (요청에서 생략하면 기본 창고)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false, updatable = false)
    private Warehouse warehouse;

    @Column(nullable = false, updatable = false)
    private LocalDate purchaseDate;

    // Σ purchase_items.line_amount
    @Column(nullable = false)
    private long totalAmount;

    // 등록자 users.user_id
    @Column(nullable = false, updatable = false)
    private Long createdBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    public static Purchase create(BusinessPartner partner, Warehouse warehouse, LocalDate purchaseDate, Long createdBy) {
        Purchase purchase = new Purchase();
        purchase.partner = partner;
        purchase.warehouse = warehouse;
        purchase.purchaseDate = purchaseDate;
        purchase.totalAmount = 0L;
        purchase.createdBy = createdBy;
        return purchase;
    }

    // 매입 라인 추가: 상품 마스터의 원가를 이 시점 값으로 스냅샷하고 총액을 갱신한다
    public PurchaseItem addItem(Item item, InventoryLot lot, int quantity) {
        PurchaseItem purchaseItem = PurchaseItem.create(this, item, lot, quantity);
        items.add(purchaseItem);
        totalAmount += purchaseItem.getLineAmount();
        return purchaseItem;
    }
}
