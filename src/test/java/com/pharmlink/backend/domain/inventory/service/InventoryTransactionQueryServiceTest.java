package com.pharmlink.backend.domain.inventory.service;

import com.pharmlink.backend.domain.inventory.dto.InventoryTransactionResponse;
import com.pharmlink.backend.domain.inventory.entity.InventoryTransaction;
import com.pharmlink.backend.domain.inventory.entity.TransactionType;
import com.pharmlink.backend.domain.inventory.repository.InventoryTransactionRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InventoryTransactionQueryServiceTest {

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @InjectMocks
    private InventoryTransactionQueryService inventoryTransactionQueryService;

    @Test
    @DisplayName("재고 이력 조회: 전체 목록을 조회한다")
    void getTransactionsSuccess() {

        InventoryTransaction transaction = transaction();

        given(inventoryTransactionRepository.search(
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(transaction)));

        Page<InventoryTransactionResponse> result =
                inventoryTransactionQueryService.getTransactions(
                        null, null, null, 1, 20
                );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).transactionId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).type()).isEqualTo(TransactionType.IN);
        assertThat(result.getContent().get(0).quantity()).isEqualTo(50);
        assertThat(result.getContent().get(0).quantityAfter()).isEqualTo(50);
    }

    @Test
    @DisplayName("재고 이력 조회: 상품 ID와 창고 ID 필터가 전달된다")
    void getTransactionsWithFilters() {

        given(inventoryTransactionRepository.search(
                eq(1L),
                eq(2L),
                eq(TransactionType.OUT),
                any(Pageable.class)
        )).willReturn(Page.empty());

        inventoryTransactionQueryService.getTransactions(
                1L, 2L, TransactionType.OUT, 1, 20
        );

        verify(inventoryTransactionRepository).search(
                eq(1L),
                eq(2L),
                eq(TransactionType.OUT),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("재고 이력 조회: 페이지 번호와 크기가 정상 적용된다")
    void getTransactionsPagination() {

        given(inventoryTransactionRepository.search(
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).willReturn(Page.empty());

        inventoryTransactionQueryService.getTransactions(
                null, null, null, 2, 10
        );

        org.mockito.ArgumentCaptor<Pageable> captor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);

        verify(inventoryTransactionRepository).search(
                eq(null),
                eq(null),
                eq(null),
                captor.capture()
        );

        Pageable pageable = captor.getValue();

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    private InventoryTransaction transaction() {

        InventoryTransaction transaction =
                org.mockito.Mockito.mock(InventoryTransaction.class);

        given(transaction.getTransactionId()).willReturn(1L);
        given(transaction.getType()).willReturn(TransactionType.IN);
        given(transaction.getQuantity()).willReturn(50);
        given(transaction.getQuantityAfter()).willReturn(50);
        given(transaction.getCreatedAt()).willReturn(Instant.now());

        return transaction;
    }
}
