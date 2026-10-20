package com.pharmlink.backend.integration;

import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// C 담당 API(8·9·10장)의 HTTP 수준 검사: 역할별 권한, 공통 오류 형식, snake_case, page_size 상한
// 실제 컨텍스트(보안 필터·예외 처리기 포함)로 요청만 보내고 데이터는 바꾸지 않는다 (없는 ID·조회 요청만 사용)
@SpringBootTest
@AutoConfigureMockMvc
class CApiAccessTest {

    private static final long UNKNOWN_ID = 999_999_999L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("8장: 창고 계정은 주문 목록 403 FORBIDDEN (주문은 관리자·영업 전용)")
    void warehouseCannotListOrders() throws Exception {
        mockMvc.perform(as(Role.WAREHOUSE, get("/api/v1/orders")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("8장: 토큰 없이 주문 목록 401 UNAUTHORIZED")
    void ordersRequireToken() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("8.1: status 값 오류는 422 UNPROCESSABLE_ENTITY")
    void invalidOrderStatusFilter() throws Exception {
        mockMvc.perform(as(Role.ADMIN, get("/api/v1/orders").param("status", "FOO")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    @DisplayName("8.1: page_size는 최대 100으로 제한, 응답 필드는 snake_case")
    void pageSizeIsCappedAndSnakeCase() throws Exception {
        mockMvc.perform(as(Role.SALES, get("/api/v1/orders").param("page_size", "500")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.pagination.page_size").value(100))
                .andExpect(jsonPath("$.pagination.total_count").exists())
                .andExpect(jsonPath("$.meta.request_id").exists());
    }

    @Test
    @DisplayName("9.3: 영업 계정은 출고 완료 403 (권한 확인이 납품 존재 확인보다 먼저)")
    void salesCannotShip() throws Exception {
        mockMvc.perform(as(Role.SALES, post("/api/v1/deliveries/" + UNKNOWN_ID + "/ship")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("9.4: 영업 계정은 납품 완료 403")
    void salesCannotComplete() throws Exception {
        mockMvc.perform(as(Role.SALES, post("/api/v1/deliveries/" + UNKNOWN_ID + "/complete")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("9.4: 창고 계정은 권한 통과, 없는 납품이면 404 DELIVERY_NOT_FOUND")
    void warehouseCompleteUnknownDelivery() throws Exception {
        mockMvc.perform(as(Role.WAREHOUSE, post("/api/v1/deliveries/" + UNKNOWN_ID + "/complete")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DELIVERY_NOT_FOUND"));
    }

    @Test
    @DisplayName("9.1: 영업 계정도 납품 목록 조회 가능 (전 역할)")
    void salesCanListDeliveries() throws Exception {
        mockMvc.perform(as(Role.SALES, get("/api/v1/deliveries")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("9.5: 없는 납품의 납품서는 PDF가 아니라 공통 오류 JSON 404 DELIVERY_NOT_FOUND")
    void documentOfUnknownDeliveryIsJson() throws Exception {
        mockMvc.perform(as(Role.SALES, get("/api/v1/deliveries/" + UNKNOWN_ID + "/document")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DELIVERY_NOT_FOUND"));
    }

    @Test
    @DisplayName("10장: 영업 계정은 매입 목록 403 (매입은 관리자·창고 전용)")
    void salesCannotListPurchases() throws Exception {
        mockMvc.perform(as(Role.SALES, get("/api/v1/purchases")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("10.3: 창고 계정은 권한 통과, 없는 매입이면 404 PURCHASE_NOT_FOUND")
    void warehouseGetsUnknownPurchase() throws Exception {
        mockMvc.perform(as(Role.WAREHOUSE, get("/api/v1/purchases/" + UNKNOWN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PURCHASE_NOT_FOUND"));
    }

    @Test
    @DisplayName("11장: 창고 계정은 매출 목록·마진 집계 403 (매출·마진은 관리자·영업 전용)")
    void warehouseCannotReadSales() throws Exception {
        mockMvc.perform(as(Role.WAREHOUSE, get("/api/v1/sales")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mockMvc.perform(as(Role.WAREHOUSE, get("/api/v1/margins/summary")
                        .param("start_date", "2026-09-01").param("end_date", "2026-09-30")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("11.1: 영업 계정 매출 목록 200, 기간 역전은 422 INVALID_DATE_RANGE")
    void salesListAccessAndDateRange() throws Exception {
        mockMvc.perform(as(Role.SALES, get("/api/v1/sales")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page_size").value(20));
        mockMvc.perform(as(Role.ADMIN, get("/api/v1/sales")
                        .param("start_date", "2026-10-31").param("end_date", "2026-10-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("11.2: 없는 매출이면 404 SALE_NOT_FOUND")
    void unknownSale() throws Exception {
        mockMvc.perform(as(Role.SALES, get("/api/v1/sales/" + UNKNOWN_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SALE_NOT_FOUND"));
    }

    @Test
    @DisplayName("11.3: start_date·end_date 누락 400 VALIDATION_ERROR, group_by 값 오류 422, 기간 역전 422")
    void marginSummaryValidation() throws Exception {
        mockMvc.perform(as(Role.ADMIN, get("/api/v1/margins/summary").param("end_date", "2026-09-30")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(Role.ADMIN, get("/api/v1/margins/summary")
                        .param("start_date", "2026-09-01").param("end_date", "2026-09-30").param("group_by", "FOO")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"));
        mockMvc.perform(as(Role.ADMIN, get("/api/v1/margins/summary")
                        .param("start_date", "2026-09-30").param("end_date", "2026-09-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("11.3: group_by 없으면 summary만(groups 필드 없음), 금액 필드는 snake_case")
    void marginSummaryShape() throws Exception {
        mockMvc.perform(as(Role.SALES, get("/api/v1/margins/summary")
                        .param("start_date", "2000-01-01").param("end_date", "2000-01-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.sales_amount").value(0))
                .andExpect(jsonPath("$.data.summary.margin_rate").value(0))
                .andExpect(jsonPath("$.data.groups").doesNotExist());
        mockMvc.perform(as(Role.SALES, get("/api/v1/margins/summary")
                        .param("start_date", "2000-01-01").param("end_date", "2000-01-31").param("group_by", "ITEM")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups").isArray());
    }

    @Test
    @DisplayName("12장: 대시보드 3개 API는 관리자·영업·창고 모두 200, 토큰 없으면 401")
    void dashboardForAllRoles() throws Exception {
        for (Role role : Role.values()) {
            mockMvc.perform(as(role, get("/api/v1/dashboard/summary"))).andExpect(status().isOk());
            mockMvc.perform(as(role, get("/api/v1/dashboard/low-stock-items"))).andExpect(status().isOk());
            mockMvc.perform(as(role, get("/api/v1/dashboard/recent-orders"))).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("12.1: 기간 미지정이면 period가 이번 달 1일~오늘, snake_case 필드, 기간 역전은 422")
    void dashboardSummaryShape() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        mockMvc.perform(as(Role.WAREHOUSE, get("/api/v1/dashboard/summary")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.start_date").value(today.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.data.period.end_date").value(today.toString()))
                .andExpect(jsonPath("$.data.total_sales").exists())
                .andExpect(jsonPath("$.data.expiring_soon_count").exists());
        mockMvc.perform(as(Role.ADMIN, get("/api/v1/dashboard/summary")
                        .param("start_date", "2026-09-30").param("end_date", "2026-09-01")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    @DisplayName("12.2·12.3: 재고 부족 기본 page_size 10, 최근 주문 limit 51은 422 UNPROCESSABLE_ENTITY")
    void dashboardPagingAndLimit() throws Exception {
        mockMvc.perform(as(Role.SALES, get("/api/v1/dashboard/low-stock-items")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.page_size").value(10));
        mockMvc.perform(as(Role.SALES, get("/api/v1/dashboard/recent-orders").param("limit", "51")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("UNPROCESSABLE_ENTITY"));
    }

    private MockHttpServletRequestBuilder as(Role role, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, role));
    }
}
