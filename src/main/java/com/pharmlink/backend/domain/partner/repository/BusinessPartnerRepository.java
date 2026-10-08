package com.pharmlink.backend.domain.partner.repository;

import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessPartnerRepository extends JpaRepository<BusinessPartner, Long> {

    boolean existsByBusinessNumber(String businessNumber);

    // 수정 시 자기 자신은 중복 검사에서 제외
    boolean existsByBusinessNumberAndPartnerIdNot(String businessNumber, Long partnerId);

    // 거래처 목록: 각 조건이 null이면 그 조건은 적용하지 않음. keyword는 거래처명 또는 사업자등록번호 부분 일치
    // (keyword의 %, _는 서비스에서 이스케이프해 문자 그대로 검색)
    @Query("""
            select p from BusinessPartner p
            where (:partnerType is null or p.partnerType = :partnerType)
              and (:keyword is null or p.name like concat('%', :keyword, '%') escape '\\'
                                    or p.businessNumber like concat('%', :keyword, '%') escape '\\')
              and (:includeInactive = true or p.isActive = true)
            """)
    Page<BusinessPartner> search(@Param("partnerType") PartnerType partnerType,
                                 @Param("keyword") String keyword,
                                 @Param("includeInactive") boolean includeInactive,
                                 Pageable pageable);
}
