package com.pharmlink.backend.domain.sales.service;

import com.pharmlink.backend.domain.sales.dto.MarginGroupBy;
import com.pharmlink.backend.domain.sales.dto.MarginRow;
import com.pharmlink.backend.domain.sales.dto.MarginSummaryResponse;
import com.pharmlink.backend.domain.sales.repository.SaleRepository;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// SalesService 단위 테스트 (조회 조건 검증·응답 조립). 실제 집계 쿼리는 PurchaseOrderFlowIntegrationTest에서 검증
@ExtendWith(MockitoExtension.class)
class SalesServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    @Mock
    private SaleRepository saleRepository;
    @InjectMocks
    private SalesService salesService;

    @Test
    @DisplayName("목록: 시작일이 종료일보다 늦으면 422 INVALID_DATE_RANGE, 조회하지 않음")
    void listWithInvalidDateRange() {
        assertThatThrownBy(() -> salesService.getSales(null, END, START, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DATE_RANGE);
        verify(saleRepository, never()).search(any(), any(), any(), any());
    }

    @Test
    @DisplayName("상세: 없는 매출이면 404 SALE_NOT_FOUND")
    void detailOfUnknownSale() {
        given(saleRepository.findDetailById(301L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> salesService.getSale(301L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_NOT_FOUND);
    }

    @Test
    @DisplayName("마진 집계: 시작일이 종료일보다 늦으면 422 INVALID_DATE_RANGE")
    void marginWithInvalidDateRange() {
        assertThatThrownBy(() -> salesService.getMarginSummary(END, START, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("마진 집계: group_by가 없으면 summary만 (groups null), 명세 11.3 예시 12,500,000 / 9,000,000 → 28.00")
    void marginSummaryOnly() {
        given(saleRepository.summarize(START, END, null, null))
                .willReturn(new MarginRow(null, null, null, 12_500_000L, 9_000_000L));

        MarginSummaryResponse response = salesService.getMarginSummary(START, END, null, null, null);

        assertThat(response.summary().marginAmount()).isEqualTo(3_500_000L);
        assertThat(response.summary().marginRate()).isEqualByComparingTo("28.00");
        assertThat(response.groups()).isNull();
        verify(saleRepository, never()).summarizeByPartner(any(), any(), any(), any());
    }

    @Test
    @DisplayName("마진 집계: group_by=PARTNER면 거래처별 금액·마진율, 명세 예시 1,800,000 / 1,400,000 → 22.22")
    void marginByPartner() {
        given(saleRepository.summarize(START, END, null, null))
                .willReturn(new MarginRow(null, null, null, 1_800_000L, 1_400_000L));
        given(saleRepository.summarizeByPartner(START, END, null, null))
                .willReturn(List.of(new MarginRow(11L, null, "우리약국", 1_800_000L, 1_400_000L)));

        MarginSummaryResponse response = salesService.getMarginSummary(START, END, MarginGroupBy.PARTNER, null, null);

        assertThat(response.groups()).singleElement()
                .isInstanceOfSatisfying(MarginSummaryResponse.PartnerGroup.class, group -> {
                    assertThat(group.partnerId()).isEqualTo(11L);
                    assertThat(group.partnerName()).isEqualTo("우리약국");
                    assertThat(group.marginAmount()).isEqualTo(400_000L);
                    assertThat(group.marginRate()).isEqualByComparingTo("22.22");
                });
    }

    @Test
    @DisplayName("마진 집계: 기간 내 매출이 없으면 금액 0, 마진율 0.00 (0으로 나누지 않음)")
    void marginWithNoSales() {
        given(saleRepository.summarize(START, END, null, null)).willReturn(new MarginRow(null, null, null, 0L, 0L));

        MarginSummaryResponse response = salesService.getMarginSummary(START, END, null, null, null);

        assertThat(response.summary().salesAmount()).isZero();
        assertThat(response.summary().marginRate()).isEqualByComparingTo("0.00");
    }
}
