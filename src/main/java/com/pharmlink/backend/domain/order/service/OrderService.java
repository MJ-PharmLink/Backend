package com.pharmlink.backend.domain.order.service;

import com.pharmlink.backend.domain.delivery.entity.Delivery;
import com.pharmlink.backend.domain.delivery.repository.DeliveryRepository;
import com.pharmlink.backend.domain.inventory.dto.StockRequest;
import com.pharmlink.backend.domain.inventory.entity.Warehouse;
import com.pharmlink.backend.domain.inventory.repository.WarehouseRepository;
import com.pharmlink.backend.domain.inventory.service.InventoryStockService;
import com.pharmlink.backend.domain.item.entity.Item;
import com.pharmlink.backend.domain.item.repository.ItemRepository;
import com.pharmlink.backend.domain.order.dto.OrderApproveResponse;
import com.pharmlink.backend.domain.order.dto.OrderCancelRequest;
import com.pharmlink.backend.domain.order.dto.OrderCancelResponse;
import com.pharmlink.backend.domain.order.dto.OrderCreateRequest;
import com.pharmlink.backend.domain.order.dto.OrderCreateResponse;
import com.pharmlink.backend.domain.order.dto.OrderDetailResponse;
import com.pharmlink.backend.domain.order.dto.OrderResponse;
import com.pharmlink.backend.domain.order.entity.Order;
import com.pharmlink.backend.domain.order.entity.OrderStatus;
import com.pharmlink.backend.domain.order.repository.OrderRepository;
import com.pharmlink.backend.domain.partner.entity.BusinessPartner;
import com.pharmlink.backend.domain.partner.service.PartnerService;
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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    // 주문번호의 날짜와 조회 기간(start_date, end_date)은 한국 날짜 기준
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ORDER_NUMBER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Sort LATEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("orderId"));

    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final DeliveryRepository deliveryRepository;
    private final PartnerService partnerService;
    private final WarehouseRepository warehouseRepository;
    private final InventoryStockService inventoryStockService;
    private final TransactionTemplate transactionTemplate;

    // 주문 목록 (최신순). 기간은 created_at 기준, KST 시작일 00:00 ~ 종료일 다음 날 00:00 전까지
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(OrderStatus status, Long partnerId, LocalDate startDate, LocalDate endDate,
                                         Integer page, Integer pageSize) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE);
        }
        Instant from = startDate == null ? null : startDate.atStartOfDay(KST).toInstant();
        Instant to = endDate == null ? null : endDate.plusDays(1).atStartOfDay(KST).toInstant();
        return orderRepository.search(status, partnerId, from, to, PageRequestFactory.of(page, pageSize, LATEST_FIRST))
                .map(OrderResponse::from);
    }

    // 주문 등록. 단가는 서버가 상품 마스터에서 스냅샷, 재고는 승인 때 판단
    // 주문번호는 등록 트랜잭션 안에서 MAX(일련번호)+1로 채번하고, 동시 요청으로 UNIQUE 충돌이 나면 1회 재시도
    public OrderCreateResponse createOrder(Long userId, OrderCreateRequest request) {
        BusinessPartner partner = partnerService.getActiveCustomer(request.partnerId());
        List<OrderCreateRequest.Line> lines = validateLines(request.items());
        Map<Long, Item> items = findActiveItems(lines);

        try {
            return transactionTemplate.execute(status -> save(partner, userId, lines, items));
        } catch (DataIntegrityViolationException e) {
            log.warn("주문번호 충돌로 채번을 다시 시도합니다.");
            return transactionTemplate.execute(status -> save(partner, userId, lines, items));
        }
    }

    // 주문 상세 (단가·원가 스냅샷 포함)
    @Transactional(readOnly = true)
    public OrderDetailResponse getOrder(Long orderId) {
        Order order = orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        Long deliveryId = deliveryRepository.findDeliveryIdByOrderId(orderId).orElse(null);
        return OrderDetailResponse.from(order, deliveryId);
    }

    // 주문 승인 (8.4): 주문 행 잠금 → PENDING 재확인 → 단종 상품 확인 → 기본 창고 가용 재고 확인·FEFO 차감·출고 이력
    // → APPROVED → 납품(WAITING) 생성. 한 트랜잭션이라 어느 단계든 실패하면 재고 차감까지 전부 rollback
    @Transactional
    public OrderApproveResponse approveOrder(Long orderId, Long userId) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() == OrderStatus.APPROVED) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_APPROVED);
        }
        if (!order.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        if (order.getItems().stream().anyMatch(orderItem -> !orderItem.getItem().isActive())) {
            throw new BusinessException(ErrorCode.ITEM_INACTIVE);
        }

        Warehouse warehouse = warehouseRepository.findDefault()
                .orElseThrow(() -> new BusinessException(ErrorCode.WAREHOUSE_NOT_FOUND));
        List<StockRequest> requests = order.getItems().stream()
                .map(orderItem -> new StockRequest(orderItem.getItem(), orderItem.getQuantity()))
                .toList();
        inventoryStockService.shipOrder(order.getOrderId(), warehouse, requests, userId);

        // DB TIMESTAMP(초 단위)에 저장되는 값과 응답 값을 맞추기 위해 초 미만은 버린다
        order.approve(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        Delivery delivery = deliveryRepository.save(Delivery.create(order));
        return OrderApproveResponse.from(order, delivery);
    }

    // 주문 취소: 행을 잠근 뒤 PENDING만 취소. APPROVED·CANCELLED는 409 INVALID_ORDER_STATUS
    // 등록 시 재고를 차감하지 않았으므로 재고 복구는 없음
    @Transactional
    public OrderCancelResponse cancelOrder(Long orderId, Long userId, OrderCancelRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
        String reason = request == null || !StringUtils.hasText(request.cancelReason()) ? null : request.cancelReason();
        order.cancel(userId, reason, Instant.now());
        // cancelled_at·updated_at을 응답에 바로 반영하기 위해 flush
        return OrderCancelResponse.from(orderRepository.saveAndFlush(order));
    }

    private OrderCreateResponse save(BusinessPartner partner, Long userId,
                                     List<OrderCreateRequest.Line> lines, Map<Long, Item> items) {
        Order order = Order.create(nextOrderNumber(), partner, userId);
        for (OrderCreateRequest.Line line : lines) {
            order.addItem(items.get(line.itemId()), line.quantity());
        }
        // UNIQUE(order_number) 충돌을 트랜잭션 안에서 바로 감지하기 위해 flush
        return OrderCreateResponse.from(orderRepository.saveAndFlush(order));
    }

    // ORD-YYYYMMDD-NNNN. NNNN은 전체 일련번호(날짜가 바뀌어도 리셋 없음), 9999를 넘으면 자릿수가 늘어남
    private String nextOrderNumber() {
        long sequence = orderRepository.findMaxSequence() + 1;
        return "ORD-" + LocalDate.now(KST).format(ORDER_NUMBER_DATE) + "-" + String.format("%04d", sequence);
    }

    // 수량 1 미만 또는 동일 상품 중복 → 422 INVALID_QUANTITY
    private List<OrderCreateRequest.Line> validateLines(List<OrderCreateRequest.Line> lines) {
        Set<Long> seen = new HashSet<>();
        for (OrderCreateRequest.Line line : lines) {
            if (line.quantity() < 1 || !seen.add(line.itemId())) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY);
            }
        }
        return lines;
    }

    // 없는 상품 404 ITEM_NOT_FOUND → 단종 상품 포함 409 ITEM_INACTIVE
    private Map<Long, Item> findActiveItems(List<OrderCreateRequest.Line> lines) {
        List<Long> itemIds = lines.stream().map(OrderCreateRequest.Line::itemId).toList();
        Map<Long, Item> items = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(Item::getItemId, Function.identity()));
        if (items.size() != itemIds.size()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (items.values().stream().anyMatch(item -> !item.isActive())) {
            throw new BusinessException(ErrorCode.ITEM_INACTIVE);
        }
        return items;
    }
}
