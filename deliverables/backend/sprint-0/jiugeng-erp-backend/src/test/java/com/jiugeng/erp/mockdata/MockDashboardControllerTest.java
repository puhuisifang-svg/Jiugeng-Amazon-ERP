package com.jiugeng.erp.mockdata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class MockDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesClearlyMarkedSyntheticSummary() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer mock-session-admin")
                        .header("Origin", "http://127.0.0.1:5173"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"))
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.source").value("SYNTHETIC"))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].label").value("待处理导入"))
                .andExpect(jsonPath("$.items[0].value").value(3));
    }

    @Test
    void requiresSessionAndFiltersSelectionDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MOCK_SESSION_REQUIRED"));

        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer mock-session-selection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].label").value("待复核新品"));
    }
}
