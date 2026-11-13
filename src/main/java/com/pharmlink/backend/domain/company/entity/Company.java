package com.pharmlink.backend.domain.company.entity;

import com.pharmlink.backend.global.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// company — 회사 정보 (단일 행, company_id = 1). 초기 데이터(V2)로 생성되며 생성·삭제 API는 없다
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "company")
public class Company extends BaseEntity {

    public static final long COMPANY_ID = 1L;

    @Id
    private Long companyId;

    @Column(nullable = false, length = 100)
    private String name;

    // 000-00-00000 형식
    @Column(nullable = false, length = 12)
    private String businessNumber;

    @Column(nullable = false, length = 50)
    private String representativeName;

    // 의약품 도매상 허가번호
    @Column(length = 50)
    private String wholesaleLicenseNumber;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 20)
    private String fax;

    @Column(length = 100)
    private String email;

    // 전체 교체 (선택 필드는 null이면 null로 바뀜)
    public void update(String name, String businessNumber, String representativeName,
                       String wholesaleLicenseNumber, String address, String phone, String fax, String email) {
        this.name = name;
        this.businessNumber = businessNumber;
        this.representativeName = representativeName;
        this.wholesaleLicenseNumber = wholesaleLicenseNumber;
        this.address = address;
        this.phone = phone;
        this.fax = fax;
        this.email = email;
    }
}
