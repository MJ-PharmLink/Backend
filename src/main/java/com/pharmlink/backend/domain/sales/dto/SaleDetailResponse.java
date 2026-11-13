package com.pharmlink.backend.domain.sales.dto;

import com.pharmlink.backend.domain.order.entity.OrderItem;
import com.pharmlink.backend.domain.sales.entity.Sale;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 매출 상세 응답 (11.2). 품목별 매출·원가·마진은 주문 라인 스냅샷(수량 × 판매단가 / 수량 × 원가)으로 계산
public record SaleDetailResponse(
        Long saleId,
        Long orderId,
        String orderNumber,
        Long deliveryId,
        Long partnerId,
        String partnerName,
        List<Line> items,
        long salesAmount,
        long costAmount,
        long marginAmount,
        BigDecimal marginRate,
        LocalDate saleDate
) {
    public record Line(Long itemId, String itemCode, String itemName, int quantity, long unitPrice, long unitCost,
                       long salesAmount, long costAmount, long marginAmount) {
        static Line from(OrderItem orderItem) {
            long sales = orderItem.getLineAmount();
            long cost = (long) orderItem.getQuantity() * orderItem.getUnitCost();
            return new Line(orderItem.getItem().getItemId(), orderItem.getItem().getItemCode(),
                    orderItem.getItem().getItemName(), orderItem.getQuantity(), orderItem.getUnitPrice(),
                    orderItem.getUnitCost(), sales, cost, sales - cost);
        }
    }

    public static SaleDetailResponse from(Sale sale) {
        return new SaleDetailResponse(sale.getSaleId(), sale.getOrder().getOrderId(), sale.getOrder().getOrderNumber(),
                sale.getDelivery().getDeliveryId(), sale.getPartner().getPartnerId(), sale.getPartner().getName(),
                sale.getOrder().getItems().stream().map(Line::from).toList(), sale.getSalesAmount(),
                sale.getCostAmount(), sale.getMarginAmount(), sale.getMarginRate(), sale.getSaleDate());
    }
}
