package com.jiugeng.erp.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void verifyFlywayBaseline() {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM schema_baseline
                        WHERE module_name = :moduleName AND baseline_version = :baselineVersion
                        """)
                .param("moduleName", "modular-monolith")
                .param("baselineVersion", "sprint-0")
                .query(Integer.class)
                .single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    void contextLoadsAndFlywayAppliesBaseline() {
    }
}
