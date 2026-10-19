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

    private MockHttpServletRequestBuilder as(Role role, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L, role));
    }
}
