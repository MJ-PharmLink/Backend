package com.pharmlink.backend.integration;

import com.pharmlink.backend.domain.user.entity.Role;
import com.pharmlink.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryTransactionAccessTest {

    private static final String ENDPOINT =
            "/api/v1/inventory-transactions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("7.5 ADMIN은 재고 이력을 조회할 수 있다")
    void adminCanReadTransactions() throws Exception {
        assertReadable(Role.ADMIN);
    }

    @Test
    @DisplayName("7.5 SALES는 재고 이력을 조회할 수 있다")
    void salesCanReadTransactions() throws Exception {
        assertReadable(Role.SALES);
    }

    @Test
    @DisplayName("7.5 WAREHOUSE는 재고 이력을 조회할 수 있다")
    void warehouseCanReadTransactions() throws Exception {
        assertReadable(Role.WAREHOUSE);
    }

    @Test
    @DisplayName("7.5 비로그인 사용자는 재고 이력 조회가 금지된다")
    void anonymousCannotReadTransactions() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private void assertReadable(Role role) throws Exception {
        mockMvc.perform(as(role, get(ENDPOINT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.page_size").value(20));
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
