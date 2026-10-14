package com.jiugeng.erp.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class ProductMaterialRelationControllerTest {
    private static final String PASSWORD = "Relation-Test-42";
    private static final String PRODUCT_ID = "00000000-0000-0000-0000-000000000120";
    private static final String VARIANT_A = "00000000-0000-0000-0000-000000000121";
    private static final String VARIANT_B = "00000000-0000-0000-0000-000000000122";
    private static final String MATERIAL_ID = "00000000-0000-0000-0000-000000000123";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM product_auxiliary_material");
        jdbc.update("DELETE FROM variant_attribute");
        jdbc.update("DELETE FROM product_variant");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM auxiliary_material");
        jdbc.update("DELETE FROM iam_password_reset_challenge");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
        jdbc.update("""
                INSERT INTO product (id, product_code, product_code_normalized, name_zh, status, created_by, updated_by)
                VALUES (?, 'PROD-REL', 'PROD-REL', '关系产品', 'ACTIVE', 'system', 'system')
                """, PRODUCT_ID);
        insertVariant(VARIANT_A, "SKU-REL-A", "signature-a");
        insertVariant(VARIANT_B, "SKU-REL-B", "signature-b");
        jdbc.update("""
                INSERT INTO auxiliary_material (id, material_code, material_code_normalized, name, material_type,
                    unit, status, created_by, updated_by)
                VALUES (?, 'MAT-REL', 'MAT-REL', '关系辅料', '包装', 'PCS', 'ACTIVE', 'system', 'system')
                """, MATERIAL_ID);
    }

    @Test
    void writerCanApplyToAllWithoutOverwritingIndependentAdjustments() throws Exception {
        String token = createUserAndLogin("material-owner", "PRODUCT_MATERIAL");
        String created = mvc.perform(post("/api/v1/product-material-relations")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(relation(VARIANT_A, "2.0000", "0.050000"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skuCode").value("SKU-REL-A"))
                .andReturn().getResponse().getContentAsString();
        String relationId = objectMapper.readTree(created).get("id").asText();

        mvc.perform(post("/api/v1/product-material-relations/apply-to-all")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("productId", PRODUCT_ID, "materialId", MATERIAL_ID,
                                "standardQuantity", 1, "unit", "PCS", "lossRate", 0.1, "remark", "批量"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));

        String list = mvc.perform(get("/api/v1/product-material-relations").param("productId", PRODUCT_ID)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode relations = objectMapper.readTree(list);
        assertThat(relations.findValuesAsText("standardQuantity")).containsExactlyInAnyOrder("2.0", "1.0");

        Map<String, Object> adjusted = relation(VARIANT_A, "3.5000", "0.020000");
        mvc.perform(put("/api/v1/product-material-relations/{id}", relationId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(adjusted)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.standardQuantity").value(3.5));
        mvc.perform(post("/api/v1/product-material-relations/{id}/disable", relationId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_auxiliary_material", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE resource_type='PRODUCT_AUXILIARY_MATERIAL'", String.class))
                .containsExactlyInAnyOrder("PRODUCT_MATERIAL_CREATE", "PRODUCT_MATERIAL_APPLY_ALL",
                        "PRODUCT_MATERIAL_UPDATE", "PRODUCT_MATERIAL_DISABLE");
    }

    @Test
    void rejectsDuplicatesInvalidAmountsAndInactiveReferences() throws Exception {
        String token = createUserAndLogin("material-owner", "PRODUCT_MATERIAL");
        mvc.perform(post("/api/v1/product-material-relations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(relation(VARIANT_A, "1", "0"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/product-material-relations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(relation(VARIANT_A, "1", "0"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_MATERIAL_EXISTS"));
        mvc.perform(post("/api/v1/product-material-relations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(relation(VARIANT_B, "0", "0"))))
                .andExpect(status().isBadRequest());
        jdbc.update("UPDATE auxiliary_material SET status='DISABLED' WHERE id=?", MATERIAL_ID);
        mvc.perform(post("/api/v1/product-material-relations").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(relation(VARIANT_B, "1", "0"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTIVE_MATERIAL_REQUIRED"));
    }

    @Test
    void selectionCanReadButCannotWrite() throws Exception {
        String writer = createUserAndLogin("material-owner", "PRODUCT_MATERIAL");
        mvc.perform(post("/api/v1/product-material-relations").header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON).content(json(relation(VARIANT_A, "1", "0"))))
                .andExpect(status().isOk());
        String reader = createUserAndLogin("selector", "SELECTION");
        mvc.perform(get("/api/v1/product-material-relations").param("variantId", VARIANT_A)
                        .header("Authorization", bearer(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].materialCode").value("MAT-REL"));
        mvc.perform(post("/api/v1/product-material-relations").header("Authorization", bearer(reader))
                        .contentType(MediaType.APPLICATION_JSON).content(json(relation(VARIANT_B, "1", "0"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CAPABILITY_DENIED"));
    }

    private void insertVariant(String id, String sku, String signature) {
        jdbc.update("""
                INSERT INTO product_variant (id, product_id, sku_code, sku_code_normalized, variant_name,
                    status, attribute_signature) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?)
                """, id, PRODUCT_ID, sku, sku, sku, signature);
    }

    private Map<String, Object> relation(String variantId, String quantity, String lossRate) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("productId", PRODUCT_ID); value.put("variantId", variantId); value.put("materialId", MATERIAL_ID);
        value.put("standardQuantity", quantity); value.put("unit", "PCS"); value.put("lossRate", lossRate);
        value.put("remark", "测试关系"); value.put("enabled", true);
        return value;
    }

    private String createUserAndLogin(String loginName, String role) throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        jdbc.update("""
                INSERT INTO iam_user (id, login_name, display_name, password_hash, role, status, must_change_password)
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
