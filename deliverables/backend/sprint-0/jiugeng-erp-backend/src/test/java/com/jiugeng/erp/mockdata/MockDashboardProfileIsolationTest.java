package com.jiugeng.erp.mockdata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MockDashboardProfileIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void doesNotExposeMockEndpointWithoutMockProfile() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/materials"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/inventory/balances"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/selection/candidates"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/watch/events"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/products"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/materials"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/inventory/transactions"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/inventory/ledger"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/selection/candidates/example/transition"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/watch/events/example/confirm"))
                .andExpect(status().isNotFound());
    }
}
