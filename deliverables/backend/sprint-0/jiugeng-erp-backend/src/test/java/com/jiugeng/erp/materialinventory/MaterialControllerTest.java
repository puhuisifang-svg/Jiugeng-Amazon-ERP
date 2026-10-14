package com.jiugeng.erp.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
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
class MaterialControllerTest {
    private static final String PASSWORD = "Material-Test-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM inventory_transaction");
        jdbc.update("DELETE FROM inventory_balance");
        jdbc.update("DELETE FROM product_auxiliary_material");
        jdbc.update("DELETE FROM auxiliary_material");
        jdbc.update("DELETE FROM iam_password_reset_challenge");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
    }

    @Test
    void materialWriterCanCreateReadUpdateAndLogicallyDisableMaterial() throws Exception {
        String token = createUserAndLogin("material-owner", "PRODUCT_MATERIAL");
        String body = mvc.perform(post("/api/v1/materials").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(material("MAT-001", "纸箱"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materialType").value("包装"))
                .andExpect(jsonPath("$.imageUrl").value("https://assets.example.test/box.png"))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        Map<String, Object> update = material("mat-001", "加厚纸箱");
        update.put("specification", "50cm × 40cm × 30cm");
        mvc.perform(put("/api/v1/materials/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("加厚纸箱"));
        mvc.perform(get("/api/v1/materials/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.specification").value("50cm × 40cm × 30cm"));
        mvc.perform(post("/api/v1/materials/{id}/disable", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auxiliary_material WHERE id = ?", Integer.class, id))
                .isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE resource_id = ?", String.class, id))
                .containsExactly("MATERIAL_CREATE", "MATERIAL_UPDATE", "MATERIAL_DISABLE");
    }

    @Test
    void codeIsCaseInsensitiveUniqueAndImmutable() throws Exception {
        String token = createUserAndLogin("material-owner", "PRODUCT_MATERIAL");
        String body = mvc.perform(post("/api/v1/materials").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(material("MAT-CASE", "纸箱"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();
        mvc.perform(post("/api/v1/materials").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(material("mat-case", "其他"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATERIAL_CODE_EXISTS"));
        mvc.perform(put("/api/v1/materials/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(material("MAT-CHANGED", "纸箱"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATERIAL_CODE_IMMUTABLE"));
    }

    @Test
    void validatesExternalImageUrlAndCapabilities() throws Exception {
        String writer = createUserAndLogin("material-owner", "PRODUCT_MATERIAL");
        Map<String, Object> invalid = material("MAT-BAD", "纸箱");
        invalid.put("imageUrl", "http://assets.example.test/box.png");
        mvc.perform(post("/api/v1/materials").header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MATERIAL_IMAGE_URL_INVALID"));
        mvc.perform(post("/api/v1/materials").header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(material("MAT-READ", "纸箱"))))
                .andExpect(status().isOk());
        String reader = createUserAndLogin("selector", "SELECTION");
        mvc.perform(get("/api/v1/materials").header("Authorization", bearer(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].materialCode").value("MAT-READ"));
        mvc.perform(post("/api/v1/materials").header("Authorization", bearer(reader))
                        .contentType(MediaType.APPLICATION_JSON).content(json(material("MAT-DENIED", "无权"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CAPABILITY_DENIED"));
    }

    private Map<String, Object> material(String code, String name) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("materialCode", code);
        value.put("name", name);
        value.put("materialType", "包装");
        value.put("specification", "标准");
        value.put("unit", "PCS");
        value.put("imageUrl", "https://assets.example.test/box.png");
        value.put("remark", "ERP-011 test");
        return value;
    }

    private String createUserAndLogin(String loginName, String role) throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        jdbc.update("""
                INSERT INTO iam_user
                    (id, login_name, display_name, password_hash, role, status, must_change_password)
                VALUES (?, ?, ?, ?, ?, 'ENABLED', FALSE)
                """, id, loginName, loginName, encoder.encode(PASSWORD), role);
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginName", loginName, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
}
