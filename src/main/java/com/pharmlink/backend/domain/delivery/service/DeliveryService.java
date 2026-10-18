package com.pharmlink.backend.domain.delivery.service;

import com.pharmlink.backend.domain.delivery.dto.DeliveryCompleteResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryDetailResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryResponse;
import com.pharmlink.backend.domain.delivery.dto.DeliveryShipResponse;
import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.delivery.repository.DeliveryRepository;
import com.pharmlink.backend.domain.sales.entity.Sale;
import com.pharmlink.backend.domain.sales.repository.SaleRepository;
import com.pharmlink.backend.global.common.PageRequestFactory;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    // 조회 기간과 매출일(sale_date)은 한국 날짜 기준
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("deliveryId"));

    private final DeliveryRepository deliveryRepository;
    private final SaleRepository saleRepository;

    // 납품 목록 (9.1, 최신순). 기간은 납품 생성일 기준, KST 시작일 00:00 ~ 종료일 다음 날 00:00 전까지
    @Transactional(readOnly = true)
    public Page<DeliveryResponse> getDeliveries(DeliveryStatus status, Long partnerId, LocalDate startDate,
                                                LocalDate endDate, Integer page, Integer pageSize) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        Instant from = startDate == null ? null : startDate.atStartOfDay(KST).toInstant();
        Instant to = endDate == null ? null : endDate.plusDays(1).atStartOfDay(KST).toInstant();
        return deliveryRepository.search(status, partnerId, from, to, PageRequestFactory.of(page, pageSize, LATEST_FIRST))
                .map(DeliveryResponse::from);
    }

    // 납품 상세 (9.2). 품목은 주문 라인 스냅샷
    @Transactional(readOnly = true)
    public DeliveryDetailResponse getDelivery(Long deliveryId) {
        return deliveryRepository.findDetailById(deliveryId)
                .map(DeliveryDetailResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    // 출고 완료 (9.3): 납품 행 잠금 → WAITING이면 SHIPPED, 아니면 409. 재고는 승인 때 이미 차감되어 변경 없음
    @Transactional
    public DeliveryShipResponse ship(Long deliveryId) {
        Delivery delivery = lockDelivery(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.WAITING) {
            throw new BusinessException(ErrorCode.INVALID_DELIVERY_STATUS);
        }
        delivery.ship(now());
        return DeliveryShipResponse.from(delivery);
    }

    // 납품 완료 (9.4): 납품 행 잠금 → SHIPPED 확인 → 매출 중복 확인 → DELIVERED → 주문 라인 스냅샷으로 매출 생성
    // 한 트랜잭션이라 매출 생성이 실패하면 납품 상태 변경도 rollback
    @Transactional
    public DeliveryCompleteResponse complete(Long deliveryId) {
        Delivery delivery = lockDelivery(deliveryId);
        if (delivery.getStatus() != DeliveryStatus.SHIPPED) {
            throw new BusinessException(ErrorCode.INVALID_DELIVERY_STATUS);
        }
        if (saleRepository.existsByDeliveryId(deliveryId)) {
            throw new BusinessException(ErrorCode.SALE_ALREADY_RECORDED);
        }
        Instant deliveredAt = now();
        delivery.complete(deliveredAt);
        Sale sale = Sale.of(delivery, saleDateOf(deliveredAt));
        try {
            saleRepository.saveAndFlush(sale);
        } catch (DataIntegrityViolationException e) {
            log.error("매출 생성 실패: deliveryId={}", deliveryId, e);
            throw new BusinessException(ErrorCode.SALE_CREATION_FAILED);
        }
        return DeliveryCompleteResponse.from(delivery, sale);
    }

    // 매출일은 납품 완료 시각을 KST로 바꾼 날짜 (UTC 날짜를 그대로 쓰지 않음, DB 설계서 3.16)
    static LocalDate saleDateOf(Instant deliveredAt) {
        return LocalDate.ofInstant(deliveredAt, KST);
    }

    private Delivery lockDelivery(Long deliveryId) {
        return deliveryRepository.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DELIVERY_NOT_FOUND));
    }

    // DB TIMESTAMP(초 단위)에 저장되는 값과 응답 값을 맞추기 위해 초 미만은 버린다
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
