package com.pharmlink.backend.domain.partner.service;

import com.pharmlink.backend.domain.partner.dto.PartnerCreateRequest;
import com.pharmlink.backend.domain.partner.dto.PartnerDetailResponse;
import com.pharmlink.backend.domain.partner.dto.PartnerResponse;
import com.pharmlink.backend.domain.partner.dto.PartnerUpdateRequest;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;
import com.pharmlink.backend.domain.partner.repository.BusinessPartnerRepository;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PartnerService {

    private final BusinessPartnerRepository partnerRepository;

    // 거래처 목록 (등록 순)
    public Page<PartnerResponse> getPartners(Integer page, Integer pageSize, PartnerType partnerType,
                                             String keyword, boolean includeInactive) {
        String likeKeyword = StringUtils.hasText(keyword) ? escapeLike(keyword.trim()) : null;
        return partnerRepository.search(partnerType, likeKeyword, includeInactive,
                        PageRequestFactory.of(page, pageSize, Sort.by("partnerId")))
                .map(PartnerResponse::from);
    }

    // 거래처 등록
    @Transactional
    public PartnerDetailResponse createPartner(PartnerCreateRequest request) {
        if (partnerRepository.existsByBusinessNumber(request.businessNumber())) {
            throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }
        BusinessPartner partner = BusinessPartner.create(request.partnerType(), request.name(),
                request.businessNumber(), request.phone(), request.address(), blankToNull(request.managerName()));
        return PartnerDetailResponse.from(partnerRepository.saveAndFlush(partner));
    }

    // 거래처 상세 (비활성 거래처도 조회)
    public PartnerDetailResponse getPartner(Long partnerId) {
        return PartnerDetailResponse.from(findPartner(partnerId));
    }

    // 거래처 정보 수정 (전체 교체, partner_type 변경 불가)
    @Transactional
    public PartnerDetailResponse updatePartner(Long partnerId, PartnerUpdateRequest request) {
        BusinessPartner partner = findPartner(partnerId);
        if (partnerRepository.existsByBusinessNumberAndPartnerIdNot(request.businessNumber(), partnerId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }
        partner.update(request.name(), request.businessNumber(), request.phone(), request.address(),
                blankToNull(request.managerName()));
        // updated_at을 응답에 바로 반영하기 위해 flush
        return PartnerDetailResponse.from(partnerRepository.saveAndFlush(partner));
    }

    // ===== 팀 공용 메서드: 상품·주문·매입 등록 시 거래처 검증 =====

    // 활성 공급처 조회 (상품 등록·공급처 변경, 매입 등록에서 사용)
    // 없음 404 PARTNER_NOT_FOUND → 공급처 아님 422 INVALID_PARTNER_TYPE → 비활성 409 PARTNER_INACTIVE
    public BusinessPartner getActiveSupplier(Long partnerId) {
        return getActivePartner(partnerId, PartnerType.SUPPLIER);
    }

    // 활성 고객사 조회 (주문 등록에서 사용)
    // 없음 404 PARTNER_NOT_FOUND → 고객사 아님 422 INVALID_PARTNER_TYPE → 비활성 409 PARTNER_INACTIVE
    public BusinessPartner getActiveCustomer(Long partnerId) {
        return getActivePartner(partnerId, PartnerType.CUSTOMER);
    }

    private BusinessPartner getActivePartner(Long partnerId, PartnerType expectedType) {
        BusinessPartner partner = findPartner(partnerId);
        if (partner.getPartnerType() != expectedType) {
            throw new BusinessException(ErrorCode.INVALID_PARTNER_TYPE);
        }
        if (!partner.isActive()) {
            throw new BusinessException(ErrorCode.PARTNER_INACTIVE);
        }
        return partner;
    }

    private BusinessPartner findPartner(Long partnerId) {
        return partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARTNER_NOT_FOUND));
    }

    // LIKE 검색에서 %, _ 를 와일드카드가 아닌 문자 그대로 찾도록 이스케이프
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
