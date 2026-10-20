package com.pharmlink.backend.domain.sales.service;

import com.pharmlink.backend.domain.sales.dto.MarginGroupBy;
import com.pharmlink.backend.domain.sales.dto.MarginRow;
import com.pharmlink.backend.domain.sales.dto.MarginSummaryResponse;
import com.pharmlink.backend.domain.sales.dto.SaleDetailResponse;
import com.pharmlink.backend.domain.sales.dto.SaleResponse;
import com.pharmlink.backend.domain.sales.repository.SaleRepository;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

// 매출·마진 조회 (11장). 매출은 납품 완료(9.4)에서만 생성되므로 여기서는 조회만 한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SalesService {

    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("saleDate"), Sort.Order.desc("saleId"));

    private final SaleRepository saleRepository;

    // 매출 목록 (11.1, 최신 매출일순). 기간은 sale_date 기준 양끝 포함
    public Page<SaleResponse> getSales(Long partnerId, LocalDate startDate, LocalDate endDate,
                                       Integer page, Integer pageSize) {
        validateRange(startDate, endDate);
        return saleRepository.search(partnerId, startDate, endDate, PageRequestFactory.of(page, pageSize, LATEST_FIRST))
                .map(SaleResponse::from);
    }

    // 매출 상세 (11.2). 품목별 금액은 주문 라인 스냅샷
    public SaleDetailResponse getSale(Long saleId) {
        return saleRepository.findDetailById(saleId)
                .map(SaleDetailResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.SALE_NOT_FOUND));
    }

    // 마진 집계 (11.3). 기간은 sale_date 기준 양끝 포함, 거래처·상품 필터는 summary와 groups에 모두 적용
    public MarginSummaryResponse getMarginSummary(LocalDate startDate, LocalDate endDate, MarginGroupBy groupBy,
                                                  Long partnerId, Long itemId) {
        validateRange(startDate, endDate);
        MarginRow total = saleRepository.summarize(startDate, endDate, partnerId, itemId);
        List<?> groups = groupBy == null ? null : switch (groupBy) {
            case PARTNER -> saleRepository.summarizeByPartner(startDate, endDate, partnerId, itemId).stream()
                    .map(MarginSummaryResponse.PartnerGroup::from).toList();
            case ITEM -> saleRepository.summarizeByItem(startDate, endDate, partnerId, itemId).stream()
                    .map(MarginSummaryResponse.ItemGroup::from).toList();
        };
        return MarginSummaryResponse.of(total, groups);
    }

    private static void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
    }
}
