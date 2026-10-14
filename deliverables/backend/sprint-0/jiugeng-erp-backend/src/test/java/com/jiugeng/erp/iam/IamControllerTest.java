package com.jiugeng.erp.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.IamTypes.Role;
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
        service.createUser("admin", "测试管理员", INITIAL_PASSWORD, Role.ADMIN);
    }

    @Test
    void usesTheApprovedMvpCapabilityContract() {
        assertThat(IamTypes.capabilities(Role.ADMIN)).containsExactlyInAnyOrder(
                "system.users.manage", "products.read", "products.write", "materials.read", "materials.write",
                "inventory.read", "inventory.write", "selection.read", "selection.write", "watch.read",
                "watch.write", "imports.read", "imports.template.download", "imports.validate", "imports.commit",
                "imports.cancel", "imports.result.download", "audit.read.all", "audit.read.self");
        assertThat(IamTypes.capabilities(Role.PRODUCT_MATERIAL)).containsExactlyInAnyOrder(
                "products.read", "products.write", "materials.read", "materials.write", "inventory.read",
                "inventory.write", "selection.read", "watch.read", "imports.read", "imports.template.download",
                "imports.validate", "imports.commit", "imports.cancel", "imports.result.download",
                "audit.read.self");
        assertThat(IamTypes.capabilities(Role.SELECTION)).isEqualTo(Set.of(
                "products.read", "materials.read", "selection.read", "selection.write", "watch.read",
                "watch.write", "audit.read.self"));
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
