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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryAdjustmentAccessTest {

    private static final String ENDPOINT =
            "/api/v1/inventory-adjustments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("7.4 ADMIN은 재고 조정 권한이 있다")
    void adminCanAccessAdjustmentEndpoint() throws Exception {
        mockMvc.perform(as(Role.ADMIN, invalidRequest()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("7.4 WAREHOUSE는 재고 조정이 금지된다")
    void warehouseCannotAdjustInventory() throws Exception {
        mockMvc.perform(as(Role.WAREHOUSE, invalidRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("7.4 SALES는 재고 조정이 금지된다")
    void salesCannotAdjustInventory() throws Exception {
        mockMvc.perform(as(Role.SALES, invalidRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("7.4 비로그인 사용자는 재고 조정이 금지된다")
    void anonymousCannotAdjustInventory() throws Exception {
        mockMvc.perform(invalidRequest())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private MockHttpServletRequestBuilder invalidRequest() {
        return post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "warehouse_id": 999999999,
                          "item_id": 999999999,
                          "adjustment_type": "INCREASE",
                          "quantity": 1,
                          "reason": "COUNT_CORRECTION",
                          "lot_number": "TEST-ACCESS-ONLY",
                          "expiry_date": "2099-12-31"
                        }
                        """);
    }

    private MockHttpServletRequestBuilder as(
            Role role,
            MockHttpServletRequestBuilder request
    ) {
        return request.header(
                HttpHeaders.AUTHORIZATION,
                "Bearer " + jwtProvider.createAccessToken(1L, role)
        );
    }
}
