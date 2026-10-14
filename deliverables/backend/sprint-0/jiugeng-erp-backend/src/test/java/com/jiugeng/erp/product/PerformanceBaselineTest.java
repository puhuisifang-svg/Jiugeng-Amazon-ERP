package com.jiugeng.erp.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PerformanceBaselineTest {
    private static final String PASSWORD = "Performance-42";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test void ordinaryProductListP95StaysBelowTwoSecondsOnTestHardware() throws Exception {
        for (int index = 0; index < 200; index++) {
            String code = "PERF-P-" + index;
            jdbc.update("""
                    INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by)
                    VALUES(?,?,?,?, 'ACTIVE','performance','performance')
                    """, UUID.randomUUID().toString(), code, code, "性能样本 " + index);
        }
        String token = login();
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        List<Long> samples = new ArrayList<>();
        for (int index = 0; index < 30; index++) {
            long started = System.nanoTime();
            mvc.perform(get("/api/v1/products").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            samples.add(System.nanoTime() - started);
        }
        samples.sort(Comparator.naturalOrder());
        double p95Millis = samples.get((int) Math.ceil(samples.size() * 0.95) - 1) / 1_000_000.0;
        System.out.printf("PERF product-list rows=200 samples=30 p95_ms=%.3f threshold_ms=2000%n", p95Millis);
        assertThat(p95Millis).isLessThanOrEqualTo(2_000.0);
    }

    private String login() throws Exception {
        String login = "performance-admin";
        jdbc.update("""
                INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password)
                VALUES(?,?,?,?, 'ADMIN','ENABLED',FALSE)
                """, UUID.randomUUID().toString(), login, "Performance Admin",
                new BCryptPasswordEncoder().encode(PASSWORD));
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("loginName", login, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("token").asText();
    }
}
