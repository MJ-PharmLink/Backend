package com.pharmlink.backend.domain.partner.service;

import com.pharmlink.backend.domain.partner.dto.PartnerCreateRequest;
import com.pharmlink.backend.domain.partner.dto.PartnerUpdateRequest;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.entity.PartnerType;
import com.pharmlink.backend.domain.partner.repository.BusinessPartnerRepository;
import com.pharmlink.backend.domain.partner.repository.PartnerTransactionReader;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// PartnerService 단위 테스트 (DB 없이 Repository를 Mock으로 대체)
@ExtendWith(MockitoExtension.class)
class PartnerServiceTest {

    private static final Long PARTNER_ID = 10L;

    @Mock
    private BusinessPartnerRepository partnerRepository;
    @Mock
    private PartnerTransactionReader transactionReader;

    @InjectMocks
    private PartnerService partnerService;

    @Test
    @DisplayName("등록: 이미 있는 사업자등록번호면 409 DUPLICATE_BUSINESS_NUMBER, 저장하지 않음")
    void createWithDuplicateBusinessNumber() {
        given(partnerRepository.existsByBusinessNumber("111-22-33333")).willReturn(true);
        PartnerCreateRequest request = new PartnerCreateRequest(PartnerType.SUPPLIER, "대한제약", "111-22-33333",
                "02-9876-5432", "경기도 성남시", "이대리");

        assertThatThrownBy(() -> partnerService.createPartner(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
        verify(partnerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("수정: 다른 거래처의 사업자등록번호면 409 DUPLICATE_BUSINESS_NUMBER, 값이 바뀌지 않음")
    void updateWithOtherPartnersBusinessNumber() {
        BusinessPartner partner = partner();
        given(partnerRepository.findById(PARTNER_ID)).willReturn(Optional.of(partner));
        given(partnerRepository.existsByBusinessNumberAndPartnerIdNot("222-33-44444", PARTNER_ID)).willReturn(true);
        PartnerUpdateRequest request = new PartnerUpdateRequest("행복약국", "222-33-44444", "02-1234-9999",
                "서울시 강남구", null);

        assertThatThrownBy(() -> partnerService.updatePartner(PARTNER_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_BUSINESS_NUMBER);
        assertThat(partner.getBusinessNumber()).isEqualTo("123-45-67890");
    }

    @Test
    @DisplayName("비활성화: 진행 중 거래(승인 대기 주문, 납품 완료 전 납품)가 있으면 409 PARTNER_IN_USE, 활성 유지")
    void deactivateWithInProgressTransaction() {
        BusinessPartner partner = partner();
        given(partnerRepository.findById(PARTNER_ID)).willReturn(Optional.of(partner));
        given(transactionReader.hasInProgressTransaction(PARTNER_ID)).willReturn(true);

        assertThatThrownBy(() -> partnerService.deactivatePartner(PARTNER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARTNER_IN_USE);
        assertThat(partner.isActive()).isTrue();
    }

    @Test
    @DisplayName("비활성화: 진행 중 거래가 없으면 is_active = false")
    void deactivateWithoutInProgressTransaction() {
        BusinessPartner partner = partner();
        given(partnerRepository.findById(PARTNER_ID)).willReturn(Optional.of(partner));
        given(transactionReader.hasInProgressTransaction(PARTNER_ID)).willReturn(false);

        partnerService.deactivatePartner(PARTNER_ID);

        assertThat(partner.isActive()).isFalse();
    }

    @Test
    @DisplayName("비활성화: 이미 비활성인 거래처는 진행 중 거래를 검사하지 않고 끝남")
    void deactivateAlreadyInactivePartner() {
        BusinessPartner partner = partner();
        partner.deactivate();
        given(partnerRepository.findById(PARTNER_ID)).willReturn(Optional.of(partner));

        partnerService.deactivatePartner(PARTNER_ID);

        verify(transactionReader, never()).hasInProgressTransaction(anyLong());
    }

    private BusinessPartner partner() {
        BusinessPartner partner = BusinessPartner.create(PartnerType.CUSTOMER, "행복약국", "123-45-67890",
                "02-1234-5678", "서울시 강남구", "박약사");
        ReflectionTestUtils.setField(partner, "partnerId", PARTNER_ID);
        return partner;
    }
}
