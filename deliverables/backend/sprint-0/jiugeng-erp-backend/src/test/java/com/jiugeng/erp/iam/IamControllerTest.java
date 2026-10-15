package com.jiugeng.erp.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.IamTypes.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IamControllerTest {
    private static final String INITIAL_PASSWORD = "Test-only-Initial-42";
    private static final String CHANGED_PASSWORD = "Test-only-Changed-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired IamService service;

    @BeforeEach
    void resetUsers() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
        jdbc.update("UPDATE iam_security_setting SET setting_value = '7', updated_by = NULL");
        service.createUser("admin", "测试管理员", INITIAL_PASSWORD, Role.ADMIN);
    }

    @Test
    void administratorConfiguresStaySignedInDurationAndLoginAppliesIt() throws Exception {
        mvc.perform(get("/api/v1/auth/login-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staySignedInDays").value(7));

        String adminToken = loginAndChangeAdmin();
        mvc.perform(post("/api/v1/settings/security").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("staySignedInDays", 14))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staySignedInDays").value(14));

        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginName", "admin",
                                "password", CHANGED_PASSWORD,
                                "staySignedIn", true))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant expiresAt = Instant.parse(objectMapper.readTree(body).get("expiresAt").asText());
        assertThat(Duration.between(Instant.now(), expiresAt).toHours()).isBetween(335L, 336L);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE action = 'SESSION_POLICY_UPDATE'", Integer.class))
                .isEqualTo(1);

        mvc.perform(post("/api/v1/settings/security").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("staySignedInDays", 31))))
                .andExpect(status().isBadRequest());

        service.createUser("selector", "选品员", INITIAL_PASSWORD, Role.SELECTION);
        String selectorToken = login("selector", INITIAL_PASSWORD);
        changePassword(selectorToken, INITIAL_PASSWORD, CHANGED_PASSWORD);
        mvc.perform(get("/api/v1/settings/security").header("Authorization", bearer(selectorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAPABILITY_DENIED"));
    }

    @Test
    void usesTheApprovedMvpCapabilityContract() {
        assertThat(IamTypes.capabilities(Role.ADMIN)).containsExactlyInAnyOrder(
                "system.users.manage", "products.read", "products.write", "materials.read", "materials.write",
                "inventory.read", "inventory.write", "selection.read", "selection.write", "watch.read",
                "watch.write", "imports.read", "imports.template.download", "imports.validate", "imports.commit",
                "imports.cancel", "imports.result.download", "audit.read.all", "audit.read.self", "dashboard.read",
                "dashboard.shipments.manage", "dashboard.pending-products.manage");
        assertThat(IamTypes.capabilities(Role.PRODUCT_MATERIAL)).containsExactlyInAnyOrder(
                "products.read", "products.write", "materials.read", "materials.write", "inventory.read",
                "inventory.write", "selection.read", "watch.read", "imports.read", "imports.template.download",
                "imports.validate", "imports.commit", "imports.cancel", "imports.result.download",
                "audit.read.self", "dashboard.read", "dashboard.shipments.manage",
                "dashboard.pending-products.manage");
        assertThat(IamTypes.capabilities(Role.SELECTION)).isEqualTo(Set.of(
                "products.read", "materials.read", "selection.read", "selection.write", "watch.read",
                "watch.write", "audit.read.self", "dashboard.read", "dashboard.pending-products.manage"));
    }

    @Test
    void forcesFirstPasswordChangeAndEnforcesCapabilityOnUserAdministration() throws Exception {
        String adminToken = login("admin", INITIAL_PASSWORD);

        mvc.perform(get("/api/v1/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        changePassword(adminToken, INITIAL_PASSWORD, CHANGED_PASSWORD);
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].capabilities[?(@ == 'system.users.manage')]").exists());

        mvc.perform(post("/api/v1/users").header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "loginName", "selector",
                                "displayName", "测试选品员",
                                "initialPassword", INITIAL_PASSWORD,
                                "role", "SELECTION"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        String selectionToken = login("selector", INITIAL_PASSWORD);
        changePassword(selectionToken, INITIAL_PASSWORD, CHANGED_PASSWORD);
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(selectionToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAPABILITY_DENIED"));

        String selectionUserId = jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE login_name = 'selector'", String.class);
        mvc.perform(post("/api/v1/users/{id}/role", selectionUserId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "PRODUCT_MATERIAL"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRODUCT_MATERIAL"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(selectionToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void locksAfterConfiguredFailuresAndAdminCanUnlock() throws Exception {
        service.createUser("locked-user", "待锁定账号", INITIAL_PASSWORD, Role.SELECTION);

        for (int attempt = 1; attempt < 5; attempt++) {
            mvc.perform(loginRequest("locked-user", "wrong-password"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(loginRequest("locked-user", "wrong-password"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mvc.perform(loginRequest("locked-user", INITIAL_PASSWORD))
                .andExpect(status().isLocked());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE actor_login_name = 'locked-user' AND action = 'LOGIN_FAILED'",
                Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE actor_login_name = 'locked-user' AND action = 'ACCOUNT_LOCK'",
                Integer.class)).isEqualTo(1);

        String adminToken = loginAndChangeAdmin();
        String userId = jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE login_name = 'locked-user'", String.class);
        mvc.perform(post("/api/v1/users/{id}/unlock", userId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedAt").doesNotExist());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE resource_id = ? AND action = 'ACCOUNT_UNLOCK'",
                Integer.class, userId)).isEqualTo(1);

        assertThat(login("locked-user", INITIAL_PASSWORD)).isNotBlank();
    }

    @Test
    void administratorCanManuallyLockAndRestoreAnAccount() throws Exception {
        service.createUser("operator", "操作员", INITIAL_PASSWORD, Role.PRODUCT_MATERIAL);
        String operatorToken = login("operator", INITIAL_PASSWORD);
        String adminToken = loginAndChangeAdmin();
        String userId = jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE login_name = 'operator'", String.class);

        mvc.perform(post("/api/v1/users/{id}/lock", userId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedAt").isNotEmpty());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(loginRequest("operator", INITIAL_PASSWORD)).andExpect(status().isLocked());

        mvc.perform(post("/api/v1/users/{id}/unlock", userId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedAt").doesNotExist());
        assertThat(login("operator", INITIAL_PASSWORD)).isNotBlank();

        String adminId = jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE login_name = 'admin'", String.class);
        mvc.perform(post("/api/v1/users/{id}/lock", adminId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_LOCK_FORBIDDEN"));
    }

    @Test
    void resetDisableAndLogoutRevokeActiveSessions() throws Exception {
        service.createUser("operator", "操作员", INITIAL_PASSWORD, Role.PRODUCT_MATERIAL);
        String operatorToken = login("operator", INITIAL_PASSWORD);
        changePassword(operatorToken, INITIAL_PASSWORD, CHANGED_PASSWORD);
        String adminToken = loginAndChangeAdmin();
        String userId = jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE login_name = 'operator'", String.class);

        mvc.perform(post("/api/v1/users/{id}/reset-password", userId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", INITIAL_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(operatorToken)))
                .andExpect(status().isUnauthorized());

        String replacementToken = login("operator", INITIAL_PASSWORD);
        mvc.perform(post("/api/v1/users/{id}/disable", userId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(replacementToken)))
                .andExpect(status().isUnauthorized());
        mvc.perform(loginRequest("operator", INITIAL_PASSWORD))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(adminToken)))
                .andExpect(status().isUnauthorized());
    }

    private String loginAndChangeAdmin() throws Exception {
        String token = login("admin", INITIAL_PASSWORD);
        changePassword(token, INITIAL_PASSWORD, CHANGED_PASSWORD);
        return token;
    }

    private String login(String loginName, String password) throws Exception {
        String body = mvc.perform(loginRequest(loginName, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        return response.get("token").asText();
    }

    private void changePassword(String token, String currentPassword, String newPassword) throws Exception {
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", currentPassword, "newPassword", newPassword))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            String loginName, String password) throws Exception {
        return post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("loginName", loginName, "password", password)));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
