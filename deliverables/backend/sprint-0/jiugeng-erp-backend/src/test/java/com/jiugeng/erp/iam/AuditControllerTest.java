package com.jiugeng.erp.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.IamTypes.Role;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditControllerTest {
    private static final String INITIAL_PASSWORD = "Test-only-Initial-42";
    private static final String CHANGED_PASSWORD = "Test-only-Changed-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired IamService iamService;
    @Autowired AuditTrail auditTrail;

    @BeforeEach
    void resetData() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
        iamService.createUser("admin", "测试管理员", INITIAL_PASSWORD, Role.ADMIN);
        iamService.createUser("selector", "测试选品员", INITIAL_PASSWORD, Role.SELECTION);
    }

    @Test
    void administratorCanQueryFilterAndPageAllEvents() throws Exception {
        String adminToken = loginAndChange("admin");
        String selectorToken = loginAndChange("selector");

        mvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", bearer(adminToken))
                        .queryParam("actorLoginName", "selector")
                        .queryParam("outcome", "SUCCESS")
                        .queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.items[0].actorLoginName").value("selector"));

        mvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", bearer(selectorToken))
                        .queryParam("from", "2026-10-16T00:00:00Z")
                        .queryParam("to", "2026-10-15T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_TIME_RANGE_INVALID"));
    }

    @Test
    void businessRolesCanOnlyReadTheirOwnEventsAndCannotMutateAuditData() throws Exception {
        String adminToken = loginAndChange("admin");
        String selectorToken = loginAndChange("selector");
        String selectorId = jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE login_name = 'selector'", String.class);
        auditTrail.record(new AuditTrail.BusinessOperation(
                selectorId, "selector", "测试选品员", "CANDIDATE_UPDATE", "SELECTION", "candidate-001",
                true, Map.of("status", "NEW"), Map.of("status", "REVIEWING"), null));
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class);

        String body = mvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", bearer(selectorToken))
                        .queryParam("actorLoginName", "admin")
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);
        assertThat(response.get("items")).isNotEmpty();
        assertThat(response.get("items").toString()).contains("CANDIDATE_UPDATE");
        response.get("items").forEach(event ->
                assertThat(event.get("actorLoginName").asText()).isEqualTo("selector"));

        mvc.perform(post("/api/v1/audit/events")
                        .header("Authorization", bearer(selectorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/audit/events/{id}", "any-event")
                        .header("Authorization", bearer(selectorToken)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event", Integer.class)).isEqualTo(before);

        mvc.perform(get("/api/v1/audit/events").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    @Test
    void accountAuditContainsBeforeAndAfterSnapshotsWithoutSecrets() throws Exception {
        String adminToken = loginAndChange("admin");
        loginAndChange("selector");
        String selectorId = jdbc.queryForObject(
                "SELECT id FROM iam_user WHERE login_name = 'selector'", String.class);

        mvc.perform(post("/api/v1/users/{id}/reset-password", selectorId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("newPassword", "Test-only-Reset-42"))))
                .andExpect(status().isOk());

        String body = mvc.perform(get("/api/v1/audit/events")
                        .header("Authorization", bearer(adminToken))
                        .queryParam("action", "PASSWORD_RESET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].beforeValue.mustChangePassword").value(false))
                .andExpect(jsonPath("$.items[0].afterValue.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("Test-only-Reset-42")
                .doesNotContain("passwordHash")
                .doesNotContain("token");
    }

    private String loginAndChange(String loginName) throws Exception {
        String token = login(loginName, INITIAL_PASSWORD);
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", INITIAL_PASSWORD,
                                "newPassword", CHANGED_PASSWORD))))
                .andExpect(status().isOk());
        return token;
    }

    private String login(String loginName, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginName", loginName, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
