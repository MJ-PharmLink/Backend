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
class InventoryExpiringAccessTest {

    private static final String ENDPOINT =
            "/api/v1/inventories/expiring";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("7.3 ADMIN은 유통기한 임박 재고를 조회할 수 있다")
    void adminCanAccessExpiringInventories() throws Exception {
        mockMvc.perform(as(Role.ADMIN, get(ENDPOINT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination.page_size").value(20));
    }

    @Test
    @DisplayName("7.3 WAREHOUSE는 유통기한 임박 재고를 조회할 수 있다")
    void warehouseCanAccessExpiringInventories() throws Exception {
        mockMvc.perform(as(Role.WAREHOUSE, get(ENDPOINT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("7.3 SALES는 유통기한 임박 재고 조회가 차단된다")
    void salesCannotAccessExpiringInventories() throws Exception {
        mockMvc.perform(as(Role.SALES, get(ENDPOINT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("7.3 비로그인 사용자는 인증이 필요하다")
    void anonymousCannotAccessExpiringInventories() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
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
