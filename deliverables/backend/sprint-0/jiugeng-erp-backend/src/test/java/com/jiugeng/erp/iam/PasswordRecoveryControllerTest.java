package com.jiugeng.erp.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.IamTypes.RecoveryChannel;
import com.jiugeng.erp.iam.IamTypes.Role;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PasswordRecoveryControllerTest.DeliveryConfiguration.class)
class PasswordRecoveryControllerTest {
    private static final String INITIAL_PASSWORD = "Start123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired IamService service;
    @Autowired CapturingDelivery delivery;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM iam_password_reset_challenge");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
        delivery.codes.clear();
        delivery.sends = 0;
        service.createUser("recoverable", "可找回账号", INITIAL_PASSWORD, Role.SELECTION);
        jdbc.update("UPDATE iam_user SET email = ?, phone = ? WHERE login_name = 'recoverable'",
                "recover@example.com", "13800138000");
    }

    @Test
    void resetsByEmailWithoutEnumeratingUnknownAccountsAndConsumesCodeOnce() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "EMAIL", "destination", "recover@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
        String code = delivery.codes.get("recover@example.com");
        assertThat(code).matches("\\d{6}");

        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "EMAIL", "destination", "recover@example.com",
                                "code", "000000", "newPassword", "Reset123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_CODE_INVALID"));

        confirm("EMAIL", "recover@example.com", code, "Reset123");
        login("recoverable", "Reset123");
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "EMAIL", "destination", "recover@example.com",
                                "code", code, "newPassword", "Again123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_CODE_INVALID"));

        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "EMAIL", "destination", "unknown@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void resetsBySmsAndRejectsPasswordsWithoutLettersAndDigits() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "SMS", "destination", "13800138000"))))
                .andExpect(status().isOk());
        String code = delivery.codes.get("13800138000");

        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "SMS", "destination", "13800138000",
                                "code", code, "newPassword", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
        confirm("SMS", "13800138000", code, "Mobile6");
        login("recoverable", "Mobile6");
    }

    @Test
    void expiresCodesRateLimitsRequestsRevokesSessionsAndWritesAudit() throws Exception {
        request("EMAIL", "recover@example.com");
        String expiredCode = delivery.codes.get("recover@example.com");
        jdbc.update("UPDATE iam_password_reset_challenge SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' MINUTE");
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", "EMAIL", "destination", "recover@example.com",
                                "code", expiredCode, "newPassword", "Expired6"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_CODE_INVALID"));

        jdbc.update("DELETE FROM iam_password_reset_challenge");
        delivery.sends = 0;
        String oldToken = loginToken("recoverable", INITIAL_PASSWORD);
        for (int attempt = 0; attempt < 5; attempt++) request("EMAIL", "recover@example.com");
        assertThat(delivery.sends).isEqualTo(5);
        String latestCode = delivery.codes.get("recover@example.com");
        request("EMAIL", "recover@example.com");
        assertThat(delivery.sends).isEqualTo(5);

        confirm("EMAIL", "recover@example.com", latestCode, "Limited6");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = 'PASSWORD_RECOVERY' AND outcome = 'SUCCESS'",
                Integer.class)).isEqualTo(1);
    }

    private void request(String channel, String destination) throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", channel, "destination", destination))))
                .andExpect(status().isOk());
    }

    private void confirm(String channel, String destination, String code, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("channel", channel, "destination", destination,
                                "code", code, "newPassword", password))))
                .andExpect(status().isOk());
    }

    private void login(String loginName, String password) throws Exception {
        loginToken(loginName, password);
    }

    private String loginToken(String loginName, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginName", loginName, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("token").asText()).isNotBlank();
        return response.get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @TestConfiguration
    static class DeliveryConfiguration {
        @Bean
        @Primary
        CapturingDelivery capturingDelivery() {
            return new CapturingDelivery();
        }
    }

    static class CapturingDelivery implements PasswordResetDelivery {
        final Map<String, String> codes = new ConcurrentHashMap<>();
        int sends;

        @Override
        public boolean supports(RecoveryChannel channel) {
            return true;
        }

        @Override
        public void send(RecoveryChannel channel, String destination, String code, Duration validFor) {
            codes.put(destination, code);
            sends++;
        }
    }
}
