package com.pharmlink.backend.domain.partner.entity;

import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// business_partners — 고객사(CUSTOMER)와 공급처(SUPPLIER)를 partner_type으로 구분
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "business_partners",
        uniqueConstraints = @UniqueConstraint(name = "uk_bp_business_number", columnNames = "business_number"),
        indexes = {
                @Index(name = "idx_bp_type_active", columnList = "partner_type, is_active"),
                @Index(name = "idx_bp_name", columnList = "name")
        })
public class BusinessPartner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long partnerId;

    // 등록 후 변경 불가
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private PartnerType partnerType;

    @Column(nullable = false, length = 100)
    private String name;

    // 000-00-00000 형식
    @Column(nullable = false, length = 12)
    private String businessNumber;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(length = 50)
    private String managerName;

    @Column(nullable = false)
    private boolean isActive;

    public static BusinessPartner create(PartnerType partnerType, String name, String businessNumber,
                                         String phone, String address, String managerName) {
        BusinessPartner partner = new BusinessPartner();
        partner.partnerType = partnerType;
        partner.name = name;
        partner.businessNumber = businessNumber;
        partner.phone = phone;
        partner.address = address;
        partner.managerName = managerName;
        partner.isActive = true;
        return partner;
    }

    // 전체 교체 (partner_type, is_active는 바꾸지 않음)
    public void update(String name, String businessNumber, String phone, String address, String managerName) {
        this.name = name;
        this.businessNumber = businessNumber;
        this.phone = phone;
        this.address = address;
        this.managerName = managerName;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
