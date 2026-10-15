package com.pharmlink.backend.integration;

import com.pharmlink.backend.domain.inventory.dto.StockShortage;
import com.pharmlink.backend.domain.order.dto.OrderApproveResponse;
import com.pharmlink.backend.domain.order.dto.OrderCreateRequest;
import com.pharmlink.backend.domain.order.service.OrderService;
import com.pharmlink.backend.domain.purchase.dto.PurchaseCreateRequest;
import com.pharmlink.backend.domain.purchase.service.PurchaseService;
import com.pharmlink.backend.global.exception.BusinessException;
import com.pharmlink.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

// 매입 → 주문 → 승인 흐름 통합 테스트 (실제 MySQL, ./gradlew test 시 로컬 DB 필요)
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
    @DisplayName("재고 부족: 409 INSUFFICIENT_STOCK(만료 로트 제외 가용 수량), 다른 상품 재고·주문 상태·이력·납품 전부 그대로")
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
                                   supplier_id, is_active)
                values (?, '통합 테스트 상품', 5, '정', 1000, 1500, 0, ?, true)""", code, supplierId);
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
