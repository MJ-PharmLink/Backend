package com.pharmlink.backend.domain.partner.repository;

import com.pharmlink.backend.domain.partner.dto.PartnerTransactionResponse;
import com.pharmlink.backend.domain.partner.dto.PartnerTransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.StandardBasicTypes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// v_partner_transactions(주문·매출·매입 UNION ALL)를 native query로 구현
@Repository
public class NativePartnerTransactionReader implements PartnerTransactionReader {

    // 거래처 조건은 UNION 바깥이 아니라 각 SELECT 안에 넣어 partner_id 인덱스를 타게 한다
    private static final String ORDER_SELECT = """
            SELECT 'ORDER' AS type, o.status AS status, o.order_id AS reference_id,
                   o.order_number AS reference_number, o.total_amount AS amount, o.created_at AS transaction_date
            FROM orders o
            WHERE o.partner_id = :partnerId""";

    private static final String SALE_SELECT = """
            SELECT 'SALE', NULL, s.sale_id, o.order_number, s.sales_amount, s.created_at
            FROM sales s JOIN orders o ON o.order_id = s.order_id
            WHERE s.partner_id = :partnerId""";

    private static final String PURCHASE_SELECT = """
            SELECT 'PURCHASE', NULL, p.purchase_id, NULL, p.total_amount, p.created_at
            FROM purchases p
            WHERE p.partner_id = :partnerId""";

    private static final String IN_PROGRESS_SQL = """
            SELECT EXISTS (SELECT 1 FROM orders o
                           WHERE o.partner_id = :partnerId AND o.status = 'PENDING')
                OR EXISTS (SELECT 1 FROM deliveries d JOIN orders o ON o.order_id = d.order_id
                           WHERE o.partner_id = :partnerId AND d.status IN ('WAITING', 'SHIPPED'))""";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean hasInProgressTransaction(Long partnerId) {
        Number result = (Number) entityManager.createNativeQuery(IN_PROGRESS_SQL)
                .setParameter("partnerId", partnerId)
                .getSingleResult();
        return result.intValue() == 1;
    }

    @Override
    public Page<PartnerTransactionResponse> findTransactions(Long partnerId, PartnerTransactionType type,
                                                             Instant from, Instant to, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        params.put("partnerId", partnerId);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (type != null) {
            where.append(" AND t.type = :type");
            params.put("type", type.name());
        }
        if (from != null) {
            where.append(" AND t.transaction_date >= :from");
            params.put("from", from);
        }
        if (to != null) {
            where.append(" AND t.transaction_date < :to");
            params.put("to", to);
        }
        String union = "(" + ORDER_SELECT + "\nUNION ALL\n" + SALE_SELECT + "\nUNION ALL\n" + PURCHASE_SELECT + ") t";

        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) FROM " + union + where);
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // 같은 시각이면 매출(SALE)이 주문(ORDER)보다 먼저 나오도록 type 내림차순, 그다음 id 내림차순
        String sql = "SELECT t.type, t.status, t.reference_id, t.reference_number, t.amount, t.transaction_date FROM "
                + union + where
                + " ORDER BY t.transaction_date DESC, t.type DESC, t.reference_id DESC LIMIT :limit OFFSET :offset";
        Query query = entityManager.createNativeQuery(sql);
        params.forEach(query::setParameter);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());

        // transaction_date는 Instant로 읽어 hibernate.jdbc.time_zone(KST) 기준으로 변환
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.unwrap(NativeQuery.class)
                .addScalar("type", StandardBasicTypes.STRING)
                .addScalar("status", StandardBasicTypes.STRING)
                .addScalar("reference_id", StandardBasicTypes.LONG)
                .addScalar("reference_number", StandardBasicTypes.STRING)
                .addScalar("amount", StandardBasicTypes.LONG)
                .addScalar("transaction_date", StandardBasicTypes.INSTANT)
                .getResultList();

        List<PartnerTransactionResponse> content = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            content.add(new PartnerTransactionResponse(PartnerTransactionType.valueOf((String) row[0]),
                    (String) row[1], (Long) row[2], (String) row[3], (Long) row[4], (Instant) row[5]));
        }
        return new PageImpl<>(content, pageable, total);
    }
}
