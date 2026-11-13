package com.pharmlink.backend.domain.inventory.dto;

import com.pharmlink.backend.domain.item.entity.Item;

// 출고 요청 1건 (주문 승인 시 주문 라인별 상품·수량)
public record StockRequest(Item item, int quantity) {
}
