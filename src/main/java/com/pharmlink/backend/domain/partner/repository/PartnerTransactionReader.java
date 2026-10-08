package com.pharmlink.backend.domain.partner.repository;

import com.pharmlink.backend.domain.partner.dto.PartnerTransactionResponse;
import com.pharmlink.backend.domain.partner.dto.PartnerTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

// 주문·납품·매출·매입 테이블 조회를 거래처 도메인에서 분리한 인터페이스
// 현재 구현(NativePartnerTransactionReader)은 Flyway로 만들어진 테이블을 native query로 직접 읽는다
public interface PartnerTransactionReader {

    // 승인 대기 주문(PENDING) 또는 납품 완료 전 납품(WAITING, SHIPPED)이 있는지
    boolean hasInProgressTransaction(Long partnerId);

    // 거래 이력 (최신순). type, from(포함), to(미포함)가 null이면 그 조건은 적용하지 않음
    Page<PartnerTransactionResponse> findTransactions(Long partnerId, PartnerTransactionType type,
                                                      Instant from, Instant to, Pageable pageable);
}
