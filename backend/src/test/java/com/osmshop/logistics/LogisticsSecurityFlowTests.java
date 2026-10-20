package com.osmshop.logistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class LogisticsSecurityFlowTests {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("CUSTOMER truy cập API Kho bị chặn 403 Forbidden")
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void warehouse_whenCustomerAccess_thenForbidden() throws Exception {
        mockMvc.perform(get("/api/internal/warehouse/products"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER truy cập API Giao hàng bị chặn 403 Forbidden")
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void delivery_whenCustomerAccess_thenForbidden() throws Exception {
        mockMvc.perform(get("/api/internal/delivery/shipments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("WAREHOUSE truy cập API Kho thành công 200 OK")
    @WithMockUser(username = "warehouse@test.com", roles = {"WAREHOUSE"})
    void warehouse_whenWarehouseStaffAccess_thenSuccess() throws Exception {
        mockMvc.perform(get("/api/internal/warehouse/products"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELIVERY truy cập API Giao hàng thành công 200 OK")
    @WithMockUser(username = "delivery@test.com", roles = {"DELIVERY"})
    void delivery_whenDeliveryStaffAccess_thenSuccess() throws Exception {
        mockMvc.perform(get("/api/internal/delivery/shipments"))
                .andExpect(status().isOk());
    }
}