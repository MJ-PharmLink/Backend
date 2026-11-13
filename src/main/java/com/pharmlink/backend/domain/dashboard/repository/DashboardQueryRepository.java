package com.pharmlink.backend.domain.dashboard.repository;

import com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse;
import com.pharmlink.backend.domain.dashboard.dto.RecentOrderResponse;
import com.pharmlink.backend.domain.delivery.entity.DeliveryStatus;
import com.pharmlink.backend.domain.order.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

// 대시보드(12장) 집계 조회. 여러 도메인 테이블을 읽기만 하므로 각 도메인 저장소를 고치지 않고 이곳에 모은다
// 재고 상태·유통기한 상태 규칙은 DB 설계서 "5. 파생(계산) 필드 정의"를 따른다
@Repository
public class DashboardQueryRepository {

    // 12.2 대상: 활성 상품, 안전재고 > 0, 현재 수량 <= 안전재고 (LOW_STOCK·OUT_OF_STOCK 모두 포함)
    private static final String LOW_STOCK_CONDITION = """
            from Inventory i join i.item it
            where it.isActive = true and it.safetyStock > 0 and i.quantity <= it.safetyStock
              and (:warehouseId is null or i.warehouse.warehouseId = :warehouseId)
            """;

    @PersistenceContext
    private EntityManager entityManager;

    // 12.1 기간 매출·마진 합계 (sale_date 기준, 양끝 포함) → [매출 합계, 마진 합계]
    public long[] sumSales(LocalDate startDate, LocalDate endDate) {
        Object[] row = entityManager.createQuery("""
                        select coalesce(sum(s.salesAmount), 0L), coalesce(sum(s.marginAmount), 0L) from Sale s
                        where s.saleDate >= :startDate and s.saleDate <= :endDate""", Object[].class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getSingleResult();
        return new long[]{((Number) row[0]).longValue(), ((Number) row[1]).longValue()};
    }

    // 12.1 기간 매입 합계 (purchase_date 기준, 양끝 포함)
    public long sumPurchases(LocalDate startDate, LocalDate endDate) {
        return entityManager.createQuery("""
                        select coalesce(sum(p.totalAmount), 0L) from Purchase p
                        where p.purchaseDate >= :startDate and p.purchaseDate <= :endDate""", Long.class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getSingleResult();
    }

    public long countOrders(OrderStatus status) {
        return entityManager.createQuery("select count(o) from Order o where o.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    public long countDeliveries(DeliveryStatus status) {
        return entityManager.createQuery("select count(d) from Delivery d where d.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    // 12.1 low_stock_count = 12.2 조건을 만족하는 재고 레코드 수 (창고 필터 없음)
    public long countLowStock() {
        return entityManager.createQuery("select count(i) " + LOW_STOCK_CONDITION, Long.class)
                .setParameter("warehouseId", null)
                .getSingleResult();
    }

    // 12.1 expired_count / expiring_soon_count — 재고 레코드의 expiry_status 기준
    // expiry_status: 수량 > 0인 로트 중 유통기한이 가장 빠른 로트(만료 로트 포함) 기준
    //   expiry_date < 오늘 → EXPIRED, 오늘 ≤ expiry_date ≤ 오늘 + soonDays → EXPIRING_SOON, 그 외·로트 없음 → NORMAL
    public long countExpired(LocalDate today) {
        return entityManager.createQuery("""
                        select count(i) from Inventory i
                        where (select min(l.expiryDate) from InventoryLot l
                               where l.inventory = i and l.quantity > 0) < :today""", Long.class)
                .setParameter("today", today)
                .getSingleResult();
    }

    public long countExpiringSoon(LocalDate today, LocalDate soonUntil) {
        return entityManager.createQuery("""
                        select count(i) from Inventory i
                        where (select min(l.expiryDate) from InventoryLot l
                               where l.inventory = i and l.quantity > 0) between :today and :soonUntil""", Long.class)
                .setParameter("today", today)
                .setParameter("soonUntil", soonUntil)
                .getSingleResult();
    }

    // 12.2 재고 부족 품목: 부족 수량(안전재고 − 현재 수량) 큰 순, 같으면 inventory_id 순
    public Page<LowStockItemResponse> findLowStockItems(Long warehouseId, Pageable pageable) {
        List<LowStockItemResponse> rows = entityManager.createQuery("""
                        select new com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse(
                            i.inventoryId, i.warehouse.warehouseId, it.itemId, it.itemCode, it.itemName,
                            i.quantity, it.safetyStock)
                        """ + LOW_STOCK_CONDITION + """
                        order by (it.safetyStock - i.quantity) desc, i.inventoryId asc""", LowStockItemResponse.class)
                .setParameter("warehouseId", warehouseId)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        long total = entityManager.createQuery("select count(i) " + LOW_STOCK_CONDITION, Long.class)
                .setParameter("warehouseId", warehouseId)
                .getSingleResult();
        return new PageImpl<>(rows, pageable, total);
    }

    // 12.3 최근 주문과 연결된 납품 상태 (납품이 없는 PENDING·CANCELLED 주문은 delivery 값 null)
    public List<RecentOrderResponse> findRecentOrders(int limit) {
        return entityManager.createQuery("""
                        select new com.pharmlink.backend.domain.dashboard.dto.RecentOrderResponse(
                            o.orderId, o.orderNumber, p.partnerId, p.name, o.status,
                            d.deliveryId, d.status, o.totalAmount, o.createdAt)
                        from Order o join o.partner p left join Delivery d on d.order = o
                        order by o.createdAt desc, o.orderId desc""", RecentOrderResponse.class)
                .setMaxResults(limit)
                .getResultList();
    }
}
