package com.pharmlink.backend.integration;

import com.pharmlink.backend.domain.dashboard.dto.DashboardSummaryResponse;
import com.pharmlink.backend.domain.dashboard.dto.LowStockItemResponse;
import com.pharmlink.backend.domain.dashboard.dto.RecentOrderResponse;
import com.pharmlink.backend.domain.dashboard.service.DashboardService;
import com.pharmlink.backend.domain.delivery.dto.DeliveryCompleteResponse;
import com.pharmlink.backend.domain.delivery.service.DeliveryService;
import com.pharmlink.backend.domain.inventory.dto.StockShortage;
import com.pharmlink.backend.domain.order.dto.OrderApproveResponse;
import com.pharmlink.backend.domain.order.dto.OrderCreateRequest;
import com.pharmlink.backend.domain.order.service.OrderService;
import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateRequest;
import com.pharmlink.backend.domain.purchase.service.PurchaseService;
import com.pharmlink.backend.domain.sales.dto.MarginGroupBy;
import com.pharmlink.backend.domain.sales.dto.MarginSummaryResponse;
import com.pharmlink.backend.domain.sales.dto.SaleDetailResponse;
import com.pharmlink.backend.domain.sales.dto.SaleResponse;
import com.pharmlink.backend.domain.sales.service.MarginCalculator;
import com.pharmlink.backend.domain.sales.service.SalesService;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 매입 → 주문 → 승인 → 출고 → 납품 완료(매출) 흐름 통합 테스트 (실제 MySQL, ./gradlew test 시 로컬 DB 필요)
// 만료 로트 제외·행 잠금·rollback처럼 DB에서만 확인되는 규칙을 검증한다.
// 테스트마다 거래처·상품을 새로 만들고 끝나면 관련 행을 모두 지운다 (로컬 DB의 다른 데이터는 건드리지 않음)
@SpringBootTest
class PurchaseOrderFlowIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private SalesService salesService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private JdbcTemplate jdbc;

    private Long userId;
    private Long supplierId;
    private Long customerId;
    private final List<Long> itemIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject("select min(user_id) from users", Long.class);
        supplierId = insertPartner("SUPPLIER");
        customerId = insertPartner("CUSTOMER");
    }

    @AfterEach
    void cleanUp() {
        String items = placeholders(itemIds.size());
        Object[] ids = itemIds.toArray();
        if (!itemIds.isEmpty()) {
            jdbc.update("delete from inventory_transactions where item_id in (" + items + ")", ids);
            jdbc.update("delete from purchase_items where item_id in (" + items + ")", ids);
        }
        jdbc.update("delete from purchases where partner_id = ?", supplierId);
        jdbc.update("delete from sales where partner_id = ?", customerId);
        jdbc.update("delete from deliveries where order_id in (select order_id from orders where partner_id = ?)",
                customerId);
        jdbc.update("delete from order_items where order_id in (select order_id from orders where partner_id = ?)",
                customerId);
        jdbc.update("delete from orders where partner_id = ?", customerId);
        if (!itemIds.isEmpty()) {
            jdbc.update("delete from inventory_lots where inventory_id in "
                    + "(select inventory_id from inventories where item_id in (" + items + "))", ids);
            jdbc.update("delete from inventories where item_id in (" + items + ")", ids);
            jdbc.update("delete from items where item_id in (" + items + ")", ids);
        }
        jdbc.update("delete from business_partners where partner_id in (?, ?)", supplierId, customerId);
        itemIds.clear();
    }

    @Test
    @DisplayName("매입 → 주문 → 승인: 만료 로트는 건너뛰고 유통기한 빠른 로트부터 차감, 로트별 OUT 이력, 납품 WAITING, 재승인 409")
    void approveDeductsFefoAndSkipsExpiredLots() {
        Long itemA = insertItem();
        // ① 작년 매입·이미 만료 7개 ② D+30 5개 ③ D+200 10개 → 전체 22, 가용 15
        purchase(TODAY.minusYears(1), line(itemA, 7, "EXP", TODAY.minusDays(1)));
        purchase(TODAY, line(itemA, 5, "D30", TODAY.plusDays(30)), line(itemA, 10, "D200", TODAY.plusDays(200)));
        Long orderId = order(itemA, 8);

        OrderApproveResponse response = orderService.approveOrder(orderId, userId);

        assertThat(response.deliveryStatus().name()).isEqualTo("WAITING");
        assertThat(lotQuantity(itemA, "EXP")).isEqualTo(7);
        assertThat(lotQuantity(itemA, "D30")).isZero();
        assertThat(lotQuantity(itemA, "D200")).isEqualTo(7);
        assertThat(inventoryQuantity(itemA)).isEqualTo(14);
        assertThat(jdbc.queryForList("""
                select quantity, quantity_after from inventory_transactions
                where item_id = ? and type = 'OUT' and reference_type = 'ORDER' and reference_id = ?
                order by transaction_id""", itemA, orderId))
                .extracting(row -> List.of(row.get("quantity"), row.get("quantity_after")))
                .containsExactly(List.of(5, 17), List.of(3, 14));
        assertThat(jdbc.queryForObject("select status from orders where order_id = ?", String.class, orderId))
                .isEqualTo("APPROVED");

        assertThatThrownBy(() -> orderService.approveOrder(orderId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ALREADY_APPROVED);
    }

    @Test
    @DisplayName("재고 부족: 차감 전에 전 상품을 확인해 409 INSUFFICIENT_STOCK(만료 로트 제외 가용 수량), 다른 상품 재고·주문 상태·이력·납품 변화 없음")
    void insufficientStockRollsBackEverything() {
        Long itemA = insertItem();
        Long itemB = insertItem();
        purchase(TODAY.minusYears(1), line(itemA, 7, "EXP", TODAY.minusDays(1)));
        purchase(TODAY, line(itemA, 15, "A1", TODAY.plusDays(30)), line(itemB, 5, "B1", TODAY.plusDays(30)));
        Long orderId = order(Map.of(itemA, 16, itemB, 1));

        assertThatThrownBy(() -> orderService.approveOrder(orderId, userId))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
                    assertThat(e.getDetails()).hasSize(1);
                    StockShortage shortage = (StockShortage) e.getDetails().get(0);
                    assertThat(shortage.itemId()).isEqualTo(itemA);
                    assertThat(shortage.requestedQuantity()).isEqualTo(16);
                    assertThat(shortage.availableQuantity()).isEqualTo(15);
                });

        assertThat(inventoryQuantity(itemA)).isEqualTo(22);
        assertThat(inventoryQuantity(itemB)).isEqualTo(5);
        assertThat(lotQuantity(itemB, "B1")).isEqualTo(5);
        assertThat(jdbc.queryForObject("select status from orders where order_id = ?", String.class, orderId))
                .isEqualTo("PENDING");
        assertThat(count("select count(*) from inventory_transactions where reference_type = 'ORDER' and reference_id = ?",
                orderId)).isZero();
        assertThat(count("select count(*) from deliveries where order_id = ?", orderId)).isZero();
    }

    @Test
    @DisplayName("승인 rollback: 재고 차감·출고 이력까지 쓴 뒤 납품 생성이 실패하면 재고·로트·이력·주문 상태가 모두 승인 전으로 돌아감")
    void approveRollsBackDeductionWhenDeliveryInsertFails() {
        Long itemA = insertItem();
        Long itemB = insertItem();
        purchase(TODAY, line(itemA, 10, "A1", TODAY.plusDays(30)), line(itemB, 5, "B1", TODAY.plusDays(30)));
        Long orderId = order(Map.of(itemA, 4, itemB, 2));
        // 차감이 끝난 다음 단계(납품 INSERT)에서 uk_del_order 위반이 나도록 같은 주문의 납품을 SQL로 먼저 넣는다
        jdbc.update("insert into deliveries (order_id, status) values (?, 'WAITING')", orderId);

        assertThatThrownBy(() -> orderService.approveOrder(orderId, userId)).isInstanceOf(Exception.class);

        assertThat(inventoryQuantity(itemA)).isEqualTo(10);
        assertThat(inventoryQuantity(itemB)).isEqualTo(5);
        assertThat(lotQuantity(itemA, "A1")).isEqualTo(10);
        assertThat(lotQuantity(itemB, "B1")).isEqualTo(5);
        assertThat(count("select count(*) from inventory_transactions where reference_type = 'ORDER' and reference_id = ?",
                orderId)).isZero();
        assertThat(jdbc.queryForObject("select status from orders where order_id = ?", String.class, orderId))
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("동시 매입·승인: 상품 2개를 매입은 [B, A], 승인은 [A, B]로 동시에 처리해도 교착 없이 모두 성공하고 수량이 맞음")
    void concurrentPurchaseAndApprovalOnTwoItemsDoNotDeadlock() throws Exception {
        Long itemA = insertItem();
        Long itemB = insertItem();
        purchase(TODAY, line(itemA, 100, "A1", TODAY.plusDays(60)), line(itemB, 100, "B1", TODAY.plusDays(60)));

        int rounds = 10;
        for (int round = 0; round < rounds; round++) {
            Long orderId = order(Map.of(itemA, 1, itemB, 1));
            List<Object> results = runConcurrently(
                    () -> purchaseService.createPurchase(userId, request(TODAY,
                            line(itemB, 2, "B1", TODAY.plusDays(60)), line(itemA, 2, "A1", TODAY.plusDays(60)))),
                    () -> orderService.approveOrder(orderId, userId));
            assertThat(results).as("round %d", round).noneMatch(Throwable.class::isInstance);
        }

        assertThat(inventoryQuantity(itemA)).isEqualTo(100 + rounds * (2 - 1));
        assertThat(inventoryQuantity(itemB)).isEqualTo(100 + rounds * (2 - 1));
        assertThat(lotSum(itemA)).isEqualTo(inventoryQuantity(itemA));
        assertThat(lotSum(itemB)).isEqualTo(inventoryQuantity(itemB));
    }

    @Test
    @DisplayName("동시 승인: 재고 10개에 7개짜리 주문 2건 → 1건 성공·1건 409, 음수 재고 없음, 재고 합계 = 로트 합계")
    void concurrentApprovalsOnlyOneSucceedsWhenStockIsShort() throws Exception {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "L1", TODAY.plusDays(60)));
        Long first = order(itemA, 7);
        Long second = order(itemA, 7);

        List<Object> results = runConcurrently(
                () -> orderService.approveOrder(first, userId),
                () -> orderService.approveOrder(second, userId));

        assertThat(results).filteredOn(OrderApproveResponse.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof BusinessException e
                && e.getErrorCode() == ErrorCode.INSUFFICIENT_STOCK).hasSize(1);
        assertThat(inventoryQuantity(itemA)).isEqualTo(3);
        assertThat(lotSum(itemA)).isEqualTo(3);
    }

    @Test
    @DisplayName("동시 승인: 재고가 충분하면 2건 모두 성공하고 같은 로트에서 차감한 수량이 하나도 빠지지 않음")
    void concurrentApprovalsBothSucceedWithoutLostUpdate() throws Exception {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "L1", TODAY.plusDays(60)));
        Long first = order(itemA, 3);
        Long second = order(itemA, 4);

        List<Object> results = runConcurrently(
                () -> orderService.approveOrder(first, userId),
                () -> orderService.approveOrder(second, userId));

        assertThat(results).allMatch(OrderApproveResponse.class::isInstance);
        assertThat(inventoryQuantity(itemA)).isEqualTo(3);
        assertThat(lotQuantity(itemA, "L1")).isEqualTo(3);
    }

    @Test
    @DisplayName("동시 입고: 같은 로트·같은 유통기한 매입 2건이 동시에 들어와도 수량이 모두 합산됨")
    void concurrentPurchasesIntoSameLotAreBothAdded() throws Exception {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 5, "L1", TODAY.plusDays(60)));

        List<Object> results = runConcurrently(
                () -> purchaseService.createPurchase(userId, request(TODAY, line(itemA, 3, "L1", TODAY.plusDays(60)))),
                () -> purchaseService.createPurchase(userId, request(TODAY, line(itemA, 4, "L1", TODAY.plusDays(60)))));

        assertThat(results).noneMatch(Throwable.class::isInstance);
        assertThat(lotQuantity(itemA, "L1")).isEqualTo(12);
        assertThat(inventoryQuantity(itemA)).isEqualTo(12);
    }

    @Test
    @DisplayName("취소한 주문을 승인하면 409 INVALID_ORDER_STATUS, 재고 변화 없음")
    void approveCancelledOrder() {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "L1", TODAY.plusDays(60)));
        Long orderId = order(itemA, 2);
        orderService.cancelOrder(orderId, userId, null);

        assertThatThrownBy(() -> orderService.approveOrder(orderId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        assertThat(inventoryQuantity(itemA)).isEqualTo(10);
    }

    @Test
    @DisplayName("승인 → 출고 → 납품 완료: 재고는 그대로, 매출 1건(주문 스냅샷 금액·마진율·KST 매출일·주문 거래처) 생성")
    void shipAndCompleteCreatesSale() {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "L1", TODAY.plusDays(60)));
        Long orderId = order(itemA, 4);
        OrderApproveResponse approved = orderService.approveOrder(orderId, userId);
        Long deliveryId = approved.deliveryId();
        // DB 설계서 3.13: deliveries.created_at = 주문 승인 시각 (DB 저장값 기준으로 완전히 같아야 함), 응답 approved_at은 초 단위
        Map<String, Object> times = jdbc.queryForMap("select o.approved_at, d.created_at from orders o "
                + "join deliveries d on d.order_id = o.order_id where o.order_id = ?", orderId);
        assertThat(times.get("created_at")).isEqualTo(times.get("approved_at"));
        assertThat(approved.approvedAt().getNano()).isZero();

        deliveryService.ship(deliveryId);
        assertThat(inventoryQuantity(itemA)).isEqualTo(6);
        DeliveryCompleteResponse response = deliveryService.complete(deliveryId);

        assertThat(inventoryQuantity(itemA)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select status from deliveries where delivery_id = ?", String.class, deliveryId))
                .isEqualTo("DELIVERED");
        Map<String, Object> sale = jdbc.queryForMap("select * from sales where delivery_id = ?", deliveryId);
        assertThat(sale.get("sale_id")).isEqualTo(response.saleId());
        assertThat(((Number) sale.get("order_id")).longValue()).isEqualTo(orderId);
        assertThat(((Number) sale.get("partner_id")).longValue()).isEqualTo(customerId);
        // 테스트 상품 단가 1,500 / 원가 1,000 × 4개
        assertThat(((Number) sale.get("sales_amount")).longValue()).isEqualTo(6_000L);
        assertThat(((Number) sale.get("cost_amount")).longValue()).isEqualTo(4_000L);
        assertThat(((Number) sale.get("margin_amount")).longValue()).isEqualTo(2_000L);
        assertThat(sale.get("margin_rate").toString()).isEqualTo("33.33");
        assertThat(sale.get("sale_date").toString())
                .isEqualTo(LocalDate.ofInstant(response.deliveredAt(), ZoneId.of("Asia/Seoul")).toString());
    }

    @Test
    @DisplayName("납품 완료: 매출이 이미 있으면 409 SALE_ALREADY_RECORDED, 납품은 SHIPPED 그대로·매출 1건 유지")
    void completeWithExistingSaleKeepsShipped() {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "L1", TODAY.plusDays(60)));
        Long orderId = order(itemA, 2);
        Long deliveryId = orderService.approveOrder(orderId, userId).deliveryId();
        deliveryService.ship(deliveryId);
        // 정상 흐름에서는 생길 수 없어서 SQL로 매출을 먼저 넣는다
        jdbc.update("""
                insert into sales (order_id, delivery_id, partner_id, partner_name, sales_amount, cost_amount,
                                   margin_amount, margin_rate, sale_date) values (?, ?, ?, 'IT-CUSTOMER', 0, 0, 0, 0, ?)""",
                orderId, deliveryId, customerId, TODAY);

        assertThatThrownBy(() -> deliveryService.complete(deliveryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_ALREADY_RECORDED);
        assertThat(jdbc.queryForObject("select status from deliveries where delivery_id = ?", String.class, deliveryId))
                .isEqualTo("SHIPPED");
        assertThat(count("select count(*) from sales where delivery_id = ?", deliveryId)).isEqualTo(1);
    }

    @Test
    @DisplayName("납품 완료 rollback: 상태를 DELIVERED로 바꾼 뒤 매출 저장이 실패하면 500 SALE_CREATION_FAILED, 납품은 SHIPPED·delivered_at 없음")
    void completeRollsBackStatusWhenSaleInsertFails() {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "L1", TODAY.plusDays(60)));
        Long orderId = order(itemA, 2);
        Long deliveryId = orderService.approveOrder(orderId, userId).deliveryId();
        deliveryService.ship(deliveryId);
        // 매출 INSERT에서 uk_sales_order 위반이 나도록, 같은 주문으로 다른 납품을 가리키는 매출을 SQL로 먼저 넣는다
        Long otherDeliveryId = orderService.approveOrder(order(itemA, 1), userId).deliveryId();
        jdbc.update("""
                insert into sales (order_id, delivery_id, partner_id, partner_name, sales_amount, cost_amount,
                                   margin_amount, margin_rate, sale_date) values (?, ?, ?, 'IT-CUSTOMER', 0, 0, 0, 0, ?)""",
                orderId, otherDeliveryId, customerId, TODAY);

        assertThatThrownBy(() -> deliveryService.complete(deliveryId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_CREATION_FAILED);

        Map<String, Object> delivery = jdbc.queryForMap(
                "select status, delivered_at from deliveries where delivery_id = ?", deliveryId);
        assertThat(delivery.get("status")).isEqualTo("SHIPPED");
        assertThat(delivery.get("delivered_at")).isNull();
        assertThat(count("select count(*) from sales where delivery_id = ?", deliveryId)).isZero();
    }

    @Test
    @DisplayName("동시 납품 완료: 같은 납품을 두 번 동시에 완료하면 1건 성공·1건 409, 매출은 1건만")
    void concurrentCompletesCreateOneSale() throws Exception {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "L1", TODAY.plusDays(60)));
        Long deliveryId = orderService.approveOrder(order(itemA, 3), userId).deliveryId();
        deliveryService.ship(deliveryId);

        List<Object> results = runConcurrently(
                () -> deliveryService.complete(deliveryId),
                () -> deliveryService.complete(deliveryId));

        assertThat(results).filteredOn(DeliveryCompleteResponse.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(r -> r instanceof BusinessException e
                && e.getErrorCode() == ErrorCode.INVALID_DELIVERY_STATUS).hasSize(1);
        assertThat(count("select count(*) from sales where delivery_id = ?", deliveryId)).isEqualTo(1);
    }

    @Test
    @DisplayName("매출 조회(11.1·11.2): 납품 완료 2건 → 거래처·기간 필터, 최신순, 상세 품목별 금액은 주문 라인 스냅샷")
    void salesListAndDetail() {
        Long itemA = insertItem();                                  // 단가 1,500 / 원가 1,000
        Long itemB = insertItem(1000, 600);                         // 단가 1,000 / 원가 600
        purchase(TODAY, line(itemA, 20, "A1", TODAY.plusDays(90)), line(itemB, 20, "B1", TODAY.plusDays(90)));
        Long firstSale = deliver(order(Map.of(itemA, 2)));
        Long secondSale = deliver(order(Map.of(itemA, 4, itemB, 5)));

        List<SaleResponse> sales = salesService.getSales(customerId, TODAY, TODAY, 1, 20).getContent();
        assertThat(sales).extracting(SaleResponse::saleId).containsExactly(secondSale, firstSale);
        SaleResponse second = sales.get(0);
        // 4 × 1,500 + 5 × 1,000 = 11,000 / 원가 4 × 1,000 + 5 × 600 = 7,000 / 마진 4,000 → 36.36
        assertThat(second.salesAmount()).isEqualTo(11_000L);
        assertThat(second.costAmount()).isEqualTo(7_000L);
        assertThat(second.marginAmount()).isEqualTo(4_000L);
        assertThat(second.marginRate()).isEqualByComparingTo("36.36");
        assertThat(second.saleDate()).isEqualTo(TODAY);
        assertThat(salesService.getSales(customerId, TODAY.plusDays(1), null, 1, 20).getContent()).isEmpty();

        SaleDetailResponse detail = salesService.getSale(secondSale);
        assertThat(detail.orderNumber()).startsWith("ORD-");
        assertThat(detail.items()).hasSize(2);
        SaleDetailResponse.Line lineB = detail.items().stream().filter(l -> l.itemId().equals(itemB)).findFirst()
                .orElseThrow();
        assertThat(List.of(lineB.quantity(), lineB.unitPrice(), lineB.unitCost(), lineB.salesAmount(),
                lineB.costAmount(), lineB.marginAmount())).containsExactly(5, 1000L, 600L, 5000L, 3000L, 2000L);
        assertThat(detail.items().stream().mapToLong(SaleDetailResponse.Line::salesAmount).sum())
                .isEqualTo(detail.salesAmount());

        assertThatThrownBy(() -> salesService.getSale(Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SALE_NOT_FOUND);
    }

    @Test
    @DisplayName("마진 집계(11.3): summary = 매출 합계, group_by PARTNER·ITEM, 상품 필터는 summary·groups 모두에 적용")
    void marginSummary() {
        Long itemA = insertItem();                                  // 마진율 33.33
        Long itemB = insertItem(1000, 600);                         // 마진율 40.00
        purchase(TODAY, line(itemA, 20, "A1", TODAY.plusDays(90)), line(itemB, 20, "B1", TODAY.plusDays(90)));
        deliver(order(Map.of(itemA, 2)));
        deliver(order(Map.of(itemA, 4, itemB, 5)));
        // 미승인 주문은 매출이 아니므로 집계에서 빠져야 한다
        order(Map.of(itemB, 3));

        MarginSummaryResponse total = salesService.getMarginSummary(TODAY, TODAY, null, customerId, null);
        // 매출 3,000 + 11,000 = 14,000 / 원가 2,000 + 7,000 = 9,000 / 마진 5,000 → 35.71
        assertThat(total.summary().salesAmount()).isEqualTo(14_000L);
        assertThat(total.summary().costAmount()).isEqualTo(9_000L);
        assertThat(total.summary().marginAmount()).isEqualTo(5_000L);
        assertThat(total.summary().marginRate()).isEqualByComparingTo("35.71");
        assertThat(total.groups()).isNull();

        MarginSummaryResponse byPartner = salesService.getMarginSummary(TODAY, TODAY, MarginGroupBy.PARTNER,
                customerId, null);
        assertThat(byPartner.groups()).hasSize(1).first()
                .isInstanceOfSatisfying(MarginSummaryResponse.PartnerGroup.class, group -> {
                    assertThat(group.partnerId()).isEqualTo(customerId);
                    assertThat(group.salesAmount()).isEqualTo(14_000L);
                    assertThat(group.marginRate()).isEqualByComparingTo("35.71");
                });

        MarginSummaryResponse byItem = salesService.getMarginSummary(TODAY, TODAY, MarginGroupBy.ITEM,
                customerId, null);
        // 마진 큰 순: A 6 × 500 = 3,000 → B 5 × 400 = 2,000
        assertThat(byItem.groups()).extracting("itemId").containsExactly(itemA, itemB);
        assertThat(byItem.groups()).extracting("marginRate").extracting(Object::toString)
                .containsExactly("33.33", "40.00");

        MarginSummaryResponse onlyB = salesService.getMarginSummary(TODAY, TODAY, MarginGroupBy.ITEM,
                customerId, itemB);
        assertThat(onlyB.summary().salesAmount()).isEqualTo(5_000L);
        assertThat(onlyB.summary().marginRate()).isEqualByComparingTo("40.00");
        assertThat(onlyB.groups()).extracting("itemId").containsExactly(itemB);

        MarginSummaryResponse empty = salesService.getMarginSummary(TODAY.plusDays(1), TODAY.plusDays(2), null,
                customerId, null);
        assertThat(empty.summary().salesAmount()).isZero();
        assertThat(empty.summary().marginRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("대시보드 12.1: 기간 매출·매입·마진 합계와 현재 시점 건수(승인 대기·출고 대기·출고 완료·재고 부족·유통기한 임박·만료)가 늘어난 만큼 반영")
    void dashboardSummaryReflectsNewData() {
        DashboardSummaryResponse before = dashboardService.getSummary(TODAY, TODAY);
        Long itemA = insertItem(1500, 1000, 50);                   // 안전재고 50
        purchase(TODAY, line(itemA, 10, "SOON", TODAY.plusDays(10)));   // 10개 < 50 → 재고 부족, 10일 뒤 만료 → 임박
        Long itemB = insertItem(1500, 1000, 0);
        purchase(TODAY.minusYears(1), line(itemB, 3, "OLD", TODAY.minusDays(1)));  // 이미 만료된 로트만 있음 → 만료
        deliver(order(Map.of(itemA, 2)));                               // 매출 3,000 / 마진 1,000
        Long waiting = order(Map.of(itemA, 1));
        orderService.approveOrder(waiting, userId);                     // 출고 대기 +1
        order(Map.of(itemA, 1));                                        // 승인 대기 +1

        DashboardSummaryResponse after = dashboardService.getSummary(TODAY, TODAY);

        assertThat(after.period().startDate()).isEqualTo(TODAY);
        assertThat(after.totalSales() - before.totalSales()).isEqualTo(3_000L);
        assertThat(after.totalMargin() - before.totalMargin()).isEqualTo(1_000L);
        // 매입액: 오늘 매입 10 × 1,000 (작년 매입은 기간 밖)
        assertThat(after.totalPurchases() - before.totalPurchases()).isEqualTo(10_000L);
        assertThat(after.pendingOrderCount() - before.pendingOrderCount()).isEqualTo(1);
        assertThat(after.waitingDeliveryCount() - before.waitingDeliveryCount()).isEqualTo(1);
        assertThat(after.shippedDeliveryCount()).isEqualTo(before.shippedDeliveryCount());
        assertThat(after.lowStockCount() - before.lowStockCount()).isEqualTo(1);
        assertThat(after.expiringSoonCount() - before.expiringSoonCount()).isEqualTo(1);
        assertThat(after.expiredCount() - before.expiredCount()).isEqualTo(1);
        assertThat(after.marginRate()).isEqualByComparingTo(
                MarginCalculator.marginRate(after.totalSales(), after.totalMargin()));
    }

    @Test
    @DisplayName("대시보드 12.2: 안전재고 이하 활성 상품만, 부족 수량 큰 순, 수량 0은 OUT_OF_STOCK, 단종·안전재고 0 제외")
    void dashboardLowStockItems() {
        Long low = insertItem(1500, 1000, 10);
        purchase(TODAY, line(low, 4, "L1", TODAY.plusDays(200)));          // 부족 6
        Long out = insertItem(1500, 1000, 30);
        purchase(TODAY, line(out, 2, "O1", TODAY.plusDays(200)));
        deliver(order(Map.of(out, 2)));                                    // 0개 → 부족 30, OUT_OF_STOCK
        Long enough = insertItem(1500, 1000, 5);
        purchase(TODAY, line(enough, 9, "E1", TODAY.plusDays(200)));       // 안전재고 초과 → 제외
        Long noSafety = insertItem(1500, 1000, 0);
        purchase(TODAY, line(noSafety, 1, "N1", TODAY.plusDays(200)));     // 안전재고 0 → 제외
        Long discontinued = insertItem(1500, 1000, 10);
        purchase(TODAY, line(discontinued, 1, "D1", TODAY.plusDays(200)));
        jdbc.update("update items set is_active = false where item_id = ?", discontinued);   // 단종 → 제외

        // 로컬 DB의 다른 재고 부족 행과 섞이므로 모든 페이지를 모은 뒤 이 테스트의 상품만 남긴다
        List<LowStockItemResponse> all = new ArrayList<>();
        for (int page = 1; ; page++) {
            var result = dashboardService.getLowStockItems(null, page, 100);
            all.addAll(result.getContent());
            if (!result.hasNext()) {
                break;
            }
        }
        List<LowStockItemResponse> rows = all.stream().filter(r -> itemIds.contains(r.itemId())).toList();

        assertThat(rows).extracting(LowStockItemResponse::itemId).containsExactly(out, low);
        assertThat(rows.get(0).stockStatus()).isEqualTo("OUT_OF_STOCK");
        assertThat(rows.get(0).shortageQuantity()).isEqualTo(30);
        assertThat(rows.get(1).stockStatus()).isEqualTo("LOW_STOCK");
        assertThat(List.of(rows.get(1).quantity(), rows.get(1).safetyStock(), rows.get(1).shortageQuantity()))
                .containsExactly(4, 10, 6);
        assertThat(dashboardService.getLowStockItems(null, null, null).getSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("대시보드 12.3: 최근 주문순, 납품 전 주문은 delivery null, 승인 주문은 납품 상태 포함, limit 범위 1~50")
    void dashboardRecentOrders() {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "R1", TODAY.plusDays(90)));
        Long approved = order(Map.of(itemA, 1));
        Long deliveryId = orderService.approveOrder(approved, userId).deliveryId();
        Long pending = order(Map.of(itemA, 1));

        List<RecentOrderResponse> recent = dashboardService.getRecentOrders(2);

        assertThat(recent).extracting(RecentOrderResponse::orderId).containsExactly(pending, approved);
        assertThat(recent.get(0).deliveryId()).isNull();
        assertThat(recent.get(0).deliveryStatus()).isNull();
        assertThat(recent.get(1).deliveryId()).isEqualTo(deliveryId);
        assertThat(recent.get(1).deliveryStatus().name()).isEqualTo("WAITING");
        assertThat(recent.get(1).orderStatus().name()).isEqualTo("APPROVED");
        assertThat(dashboardService.getRecentOrders(null)).hasSizeLessThanOrEqualTo(10);
        assertThatThrownBy(() -> dashboardService.getRecentOrders(51))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
        assertThatThrownBy(() -> dashboardService.getRecentOrders(0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("거래처명 복합 FK: 매입·주문·매출에 거래처명이 복사되고, 거래처명 수정 시 상품·매입·주문·매출이 같이 바뀜")
    void partnerNameFollowsCompositeForeignKey() {
        Long itemA = insertItem();
        purchase(TODAY, line(itemA, 10, "N1", TODAY.plusDays(60)));
        Long orderId = order(itemA, 2);
        Long saleId = deliver(orderId);

        assertThat(jdbc.queryForObject("select partner_name from orders where order_id = ?", String.class, orderId))
                .isEqualTo("IT-CUSTOMER");
        assertThat(jdbc.queryForObject("select partner_name from sales where sale_id = ?", String.class, saleId))
                .isEqualTo("IT-CUSTOMER");
        assertThat(jdbc.queryForObject("select partner_name from purchases where partner_id = ?", String.class,
                supplierId)).isEqualTo("IT-SUPPLIER");

        jdbc.update("update business_partners set name = 'IT-CUSTOMER-R' where partner_id = ?", customerId);
        jdbc.update("update business_partners set name = 'IT-SUPPLIER-R' where partner_id = ?", supplierId);

        assertThat(jdbc.queryForObject("select partner_name from orders where order_id = ?", String.class, orderId))
                .isEqualTo("IT-CUSTOMER-R");
        assertThat(jdbc.queryForObject("select partner_name from sales where sale_id = ?", String.class, saleId))
                .isEqualTo("IT-CUSTOMER-R");
        assertThat(jdbc.queryForObject("select partner_name from purchases where partner_id = ?", String.class,
                supplierId)).isEqualTo("IT-SUPPLIER-R");
        assertThat(jdbc.queryForObject("select supplier_name from items where item_id = ?", String.class, itemA))
                .isEqualTo("IT-SUPPLIER-R");
        // 거래처와 다른 이름은 FK 위반
        assertThatThrownBy(() -> jdbc.update("update orders set partner_name = 'WRONG' where order_id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long insertItem(long unitPrice, long unitCost, int safetyStock) {
        Long itemId = insertItem(unitPrice, unitCost);
        jdbc.update("update items set safety_stock = ? where item_id = ?", safetyStock, itemId);
        return itemId;
    }

    // 승인 → 출고 → 납품 완료 후 생성된 매출 ID
    private Long deliver(Long orderId) {
        Long deliveryId = orderService.approveOrder(orderId, userId).deliveryId();
        deliveryService.ship(deliveryId);
        return deliveryService.complete(deliveryId).saleId();
    }

    private Long insertItem(long unitPrice, long unitCost) {
        Long itemId = insertItem();
        jdbc.update("update items set unit_price = ?, unit_cost = ? where item_id = ?", unitPrice, unitCost, itemId);
        return itemId;
    }

    // 두 작업을 최대한 같은 순간에 시작시키고, 각 결과(반환값 또는 예외)를 순서대로 돌려준다
    private List<Object> runConcurrently(Callable<?>... tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return task.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private void purchase(LocalDate purchaseDate, PurchaseCreateRequest.Line... lines) {
        purchaseService.createPurchase(userId, request(purchaseDate, lines));
    }

    private PurchaseCreateRequest request(LocalDate purchaseDate, PurchaseCreateRequest.Line... lines) {
        return new PurchaseCreateRequest(supplierId, null, purchaseDate, List.of(lines));
    }

    private static PurchaseCreateRequest.Line line(Long itemId, int quantity, String lotNumber, LocalDate expiryDate) {
        return new PurchaseCreateRequest.Line(itemId, quantity, lotNumber, expiryDate);
    }

    private Long order(Long itemId, int quantity) {
        return order(Map.of(itemId, quantity));
    }

    private Long order(Map<Long, Integer> quantities) {
        List<OrderCreateRequest.Line> lines = quantities.entrySet().stream()
                .map(entry -> new OrderCreateRequest.Line(entry.getKey(), entry.getValue()))
                .toList();
        return orderService.createOrder(userId, new OrderCreateRequest(customerId, lines)).orderId();
    }

    private Long insertPartner(String type) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String businessNumber = String.format("%03d-%02d-%05d", random.nextInt(1000), random.nextInt(100),
                random.nextInt(100_000));
        return insert("""
                insert into business_partners (partner_type, name, business_number, phone, address)
                values (?, ?, ?, '02-0000-0000', '통합 테스트')""", type, "IT-" + type, businessNumber);
    }

    // 상품 등록 API(6장, B 담당)가 아직 없어 SQL로 넣는다 (카테고리 5 = 기타, 원가 1,000 / 단가 1,500)
    private Long insertItem() {
        String code = "IT-TEST-" + System.nanoTime() % 1_000_000_000_000L;
        Long itemId = insert("""
                insert into items (item_code, item_name, category_id, unit, unit_cost, unit_price, safety_stock,
                                   supplier_id, supplier_name, is_active)
                values (?, '통합 테스트 상품', 5, '정', 1000, 1500, 0, ?, 'IT-SUPPLIER', true)""", code, supplierId);
        itemIds.add(itemId);
        return itemId;
    }

    private Long insert(String sql, Object... args) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private int inventoryQuantity(Long itemId) {
        return jdbc.queryForObject("select quantity from inventories where item_id = ?", Integer.class, itemId);
    }

    private int lotQuantity(Long itemId, String lotNumber) {
        return jdbc.queryForObject("""
                select l.quantity from inventory_lots l join inventories i on i.inventory_id = l.inventory_id
                where i.item_id = ? and l.lot_number = ?""", Integer.class, itemId, lotNumber);
    }

    private int lotSum(Long itemId) {
        return jdbc.queryForObject("""
                select coalesce(sum(l.quantity), 0) from inventory_lots l
                join inventories i on i.inventory_id = l.inventory_id where i.item_id = ?""", Integer.class, itemId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private static String placeholders(int size) {
        return String.join(", ", Collections.nCopies(Math.max(size, 1), "?"));
    }
}
