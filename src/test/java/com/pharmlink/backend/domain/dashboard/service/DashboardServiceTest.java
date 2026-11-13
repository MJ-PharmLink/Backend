package com.pharmlink.backend.domain.dashboard.service;

import com.pharmlink.backend.domain.dashboard.dto.DashboardSummaryResponse;
import com.pharmlink.backend.domain.dashboard.repository.DashboardQueryRepository;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.order.entity.OrderStatus;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// DashboardService 단위 테스트 (기간 기본값·검증·응답 조립). 실제 집계 쿼리는 PurchaseOrderFlowIntegrationTest에서 검증
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);

    @Mock
    private DashboardQueryRepository dashboardQueryRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(dashboardQueryRepository, 90);
    }

    @Test
    @DisplayName("12.1: 명세 예시 매출 12,500,000 / 마진 3,500,000 → 마진율 28.00, 건수는 상태별 조회 값 그대로")
    void summaryAssemblesAmountsAndCounts() {
        given(dashboardQueryRepository.sumSales(START, END)).willReturn(new long[]{12_500_000L, 3_500_000L});
        given(dashboardQueryRepository.sumPurchases(START, END)).willReturn(9_800_000L);
        given(dashboardQueryRepository.countOrders(OrderStatus.PENDING)).willReturn(8L);
        given(dashboardQueryRepository.countDeliveries(DeliveryStatus.WAITING)).willReturn(5L);
        given(dashboardQueryRepository.countDeliveries(DeliveryStatus.SHIPPED)).willReturn(3L);
        given(dashboardQueryRepository.countLowStock()).willReturn(5L);
        given(dashboardQueryRepository.countExpiringSoon(any(), any())).willReturn(3L);
        given(dashboardQueryRepository.countExpired(any())).willReturn(1L);

        DashboardSummaryResponse response = dashboardService.getSummary(START, END);

        assertThat(response.period()).isEqualTo(new DashboardSummaryResponse.Period(START, END));
        assertThat(response.totalSales()).isEqualTo(12_500_000L);
        assertThat(response.totalPurchases()).isEqualTo(9_800_000L);
        assertThat(response.totalMargin()).isEqualTo(3_500_000L);
        assertThat(response.marginRate()).isEqualByComparingTo("28.00");
        assertThat(response.pendingOrderCount()).isEqualTo(8);
        assertThat(response.waitingDeliveryCount()).isEqualTo(5);
        assertThat(response.shippedDeliveryCount()).isEqualTo(3);
        assertThat(response.lowStockCount()).isEqualTo(5);
        assertThat(response.expiringSoonCount()).isEqualTo(3);
        assertThat(response.expiredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("12.1: 기간 미지정이면 이번 달 1일 ~ 오늘(KST), 임박 기준은 오늘 ~ 오늘+90일, 매출 0이면 마진율 0.00")
    void summaryDefaultsToThisMonth() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        given(dashboardQueryRepository.sumSales(any(), any())).willReturn(new long[]{0L, 0L});

        DashboardSummaryResponse response = dashboardService.getSummary(null, null);

        assertThat(response.period().startDate()).isEqualTo(today.withDayOfMonth(1));
        assertThat(response.period().endDate()).isEqualTo(today);
        assertThat(response.marginRate()).isEqualByComparingTo("0.00");
        verify(dashboardQueryRepository).countExpiringSoon(today, today.plusDays(90));
    }

    @Test
    @DisplayName("12.1: 시작일이 종료일보다 늦으면 422 INVALID_DATE_RANGE")
    void summaryWithInvalidDateRange() {
        assertThatThrownBy(() -> dashboardService.getSummary(END, START))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_DATE_RANGE);
        verifyNoInteractions(dashboardQueryRepository);
    }

    @Test
    @DisplayName("12.2: page_size 미지정이면 기본 10")
    void lowStockDefaultPageSize() {
        dashboardService.getLowStockItems(null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(dashboardQueryRepository).findLowStockItems(eq(null), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("12.2: 재고 조회가 DB 오류로 실패하면 500 INVENTORY_QUERY_FAILED")
    void lowStockQueryFailure() {
        given(dashboardQueryRepository.findLowStockItems(any(), any())).willThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> dashboardService.getLowStockItems(1L, 1, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVENTORY_QUERY_FAILED);
    }

    @Test
    @DisplayName("12.3: limit 미지정이면 10, 1~50 밖이면 422 UNPROCESSABLE_ENTITY")
    void recentOrdersLimit() {
        dashboardService.getRecentOrders(null);
        verify(dashboardQueryRepository).findRecentOrders(10);
        dashboardService.getRecentOrders(50);
        verify(dashboardQueryRepository).findRecentOrders(50);

        assertThatThrownBy(() -> dashboardService.getRecentOrders(51))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
        assertThatThrownBy(() -> dashboardService.getRecentOrders(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
    }
}
