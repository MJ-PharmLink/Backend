package com.pharmlink.backend.domain.dashboard.service;

import com.pharmlink.backend.domain.dashboard.dto.DashboardSummaryResponse;
import com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse;
import com.pharmlink.backend.domain.dashboard.dto.RecentOrderResponse;
import com.pharmlink.backend.domain.dashboard.repository.DashboardQueryRepository;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.order.entity.OrderStatus;
import com.pharmlink.backend.domain.sales.service.MarginCalculator;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

// 대시보드 (12장) — 조회 전용
@Slf4j
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    // 12.2 page_size 기본값은 10 (다른 목록의 기본값 20과 다름)
    private static final int LOW_STOCK_DEFAULT_PAGE_SIZE = 10;
    // 12.3 limit 기본 10, 최대 50
    private static final int RECENT_DEFAULT_LIMIT = 10;
    private static final int RECENT_MAX_LIMIT = 50;

    private final DashboardQueryRepository dashboardQueryRepository;
    // 유통기한 임박 기준 일수 (DB 설계서: "90일은 서버 설정값")
    private final int expiringSoonDays;

    public DashboardService(DashboardQueryRepository dashboardQueryRepository,
                            @Value("${pharmlink.inventory.expiring-soon-days:90}") int expiringSoonDays) {
        this.dashboardQueryRepository = dashboardQueryRepository;
        this.expiringSoonDays = expiringSoonDays;
    }

    // 12.1 요약. 기간 미지정 시 이번 달 1일 ~ 오늘(KST). 한쪽만 주면 다른 쪽은 기본값
    public DashboardSummaryResponse getSummary(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now(KST);
        LocalDate start = startDate != null ? startDate : today.withDayOfMonth(1);
        LocalDate end = endDate != null ? endDate : today;
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        long[] sales = dashboardQueryRepository.sumSales(start, end);
        long totalSales = sales[0];
        long totalMargin = sales[1];
        return new DashboardSummaryResponse(
                new DashboardSummaryResponse.Period(start, end),
                totalSales,
                dashboardQueryRepository.sumPurchases(start, end),
                totalMargin,
                MarginCalculator.marginRate(totalSales, totalMargin),
                dashboardQueryRepository.countOrders(OrderStatus.PENDING),
                dashboardQueryRepository.countDeliveries(DeliveryStatus.WAITING),
                dashboardQueryRepository.countDeliveries(DeliveryStatus.SHIPPED),
                dashboardQueryRepository.countLowStock(),
                dashboardQueryRepository.countExpiringSoon(today, today.plusDays(expiringSoonDays)),
                dashboardQueryRepository.countExpired(today));
    }

    // 12.2 재고 부족 품목. 조회 실패는 500 INVENTORY_QUERY_FAILED
    public Page<LowStockItemResponse> getLowStockItems(Long warehouseId, Integer page, Integer pageSize) {
        int size = pageSize == null || pageSize < 1 ? LOW_STOCK_DEFAULT_PAGE_SIZE : pageSize;
        try {
            return dashboardQueryRepository.findLowStockItems(warehouseId, PageRequestFactory.of(page, size));
        } catch (DataAccessException e) {
            log.error("재고 부족 품목 조회 실패: warehouseId={}", warehouseId, e);
            throw new BusinessException(ErrorCode.INVENTORY_QUERY_FAILED);
        }
    }

    // 12.3 최근 주문/납품 현황. limit은 1~50, 범위를 벗어나면 422 UNPROCESSABLE_ENTITY
    public List<RecentOrderResponse> getRecentOrders(Integer limit) {
        int size = limit == null ? RECENT_DEFAULT_LIMIT : limit;
        if (size < 1 || size > RECENT_MAX_LIMIT) {
            throw new BusinessException(ErrorCode.UNPROCESSABLE_ENTITY);
        }
        return dashboardQueryRepository.findRecentOrders(size);
    }
}
