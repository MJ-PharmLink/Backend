package com.pharmlink.backend.domain.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

// warehouses — 창고. 창고 관리 API는 없고 초기 데이터(본사 창고, is_default = true)로만 생성된다
// 매입 등록·주문 승인에서 창고를 생략하면 is_default = true 인 창고를 쓴다 (warehouse_id = 1 고정값 사용 금지)
@Getter
@Entity
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "warehouses")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long warehouseId;

    @Column(nullable = false, length = 50)
    private String name;

    private String location;

    // 기본 창고 여부. true는 1건만 허용 (애플리케이션 보장)
    @Column(nullable = false)
    private boolean isDefault;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
