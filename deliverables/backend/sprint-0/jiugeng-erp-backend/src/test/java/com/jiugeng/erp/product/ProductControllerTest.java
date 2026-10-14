package com.jiugeng.erp.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.List;
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
class ProductControllerTest {
    private static final String PASSWORD = "Product-Test-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM product_auxiliary_material");
        jdbc.update("DELETE FROM variant_attribute");
        jdbc.update("DELETE FROM product_variant");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM iam_password_reset_challenge");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
    }

    @Test
    void productWriterCanCreateReadUpdateAndLogicallyDisableProduct() throws Exception {
        String token = createUserAndLogin("product-owner", "PRODUCT_MATERIAL");
        Map<String, Object> createInput = product("PROD-001", "旅行收纳袋", "SKU-001", "Black");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> createVariants = (List<Map<String, Object>>) createInput.get("variants");
        Map<String, Object> secondVariant = new java.util.LinkedHashMap<>(createVariants.getFirst());
        secondVariant.put("skuCode", "SKU-002");
        secondVariant.put("variantName", "白色款");
        secondVariant.put("attributes", List.of(Map.of(
                "attributeCode", "color", "attributeValue", "White", "displayOrder", 0)));
        createVariants.add(secondVariant);
        String createdBody = mvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(createInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("PROD-001"))
                .andExpect(jsonPath("$.variants[0].attributes[0].attributeCode").value("color"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);
        String id = created.get("id").asText();
        String variantId = created.at("/variants/0/id").asText();

        Map<String, Object> update = product("prod-001", "旅行收纳袋升级版", "sku-001", "Blue");
        @SuppressWarnings("unchecked")
        Map<String, Object> variant = (Map<String, Object>) ((List<?>) update.get("variants")).getFirst();
        variant.put("id", variantId);
        mvc.perform(put("/api/v1/products/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nameZh").value("旅行收纳袋升级版"))
                .andExpect(jsonPath("$.variants[0].attributes[0].attributeValue").value("Blue"));

        mvc.perform(get("/api/v1/products/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skuCount").value(2));
        assertThat(jdbc.queryForObject("SELECT status FROM product_variant WHERE sku_code = 'SKU-002'", String.class))
                .isEqualTo("DISABLED");
        mvc.perform(post("/api/v1/products/{id}/disable", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.variants[0].status").value("DISABLED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE resource_id = ?", String.class, id))
                .containsExactly("PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DISABLE");
    }

    @Test
    void codesAndVariantCombinationsAreUniqueIgnoringCase() throws Exception {
        String token = createUserAndLogin("product-owner", "PRODUCT_MATERIAL");
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product("PROD-CASE", "产品一", "SKU-CASE", "Black"))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product("prod-case", "产品二", "SKU-OTHER", "White"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CODE_EXISTS"));

        Map<String, Object> duplicateCombination = product("PROD-OTHER", "产品三", "SKU-A", "Black");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> variants = (List<Map<String, Object>>) duplicateCombination.get("variants");
        Map<String, Object> second = new java.util.LinkedHashMap<>(variants.getFirst());
        second.put("skuCode", "SKU-B");
        second.put("variantName", "黑色 B");
        variants.add(second);
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(duplicateCombination)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VARIANT_COMBINATION_EXISTS"));
    }

    @Test
    void selectionRoleCanReadButCannotWrite() throws Exception {
        String writer = createUserAndLogin("product-owner", "PRODUCT_MATERIAL");
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product("PROD-READ", "只读产品", "SKU-READ", "One"))))
                .andExpect(status().isOk());
        String reader = createUserAndLogin("selector", "SELECTION");
        mvc.perform(get("/api/v1/products").header("Authorization", bearer(reader)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].productCode").value("PROD-READ"));
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(reader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product("PROD-DENIED", "无权产品", "SKU-DENIED", "One"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CAPABILITY_DENIED"));
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

    private Map<String, Object> product(String productCode, String name, String skuCode, String color) {
        Map<String, Object> attribute = new java.util.LinkedHashMap<>();
        attribute.put("attributeCode", "color");
        attribute.put("attributeValue", color);
        attribute.put("displayOrder", 0);
        Map<String, Object> variant = new java.util.LinkedHashMap<>();
        variant.put("skuCode", skuCode);
        variant.put("variantName", color + " 款");
        variant.put("status", "ACTIVE");
        variant.put("attributes", List.of(attribute));
        Map<String, Object> product = new java.util.LinkedHashMap<>();
        product.put("productCode", productCode);
        product.put("nameZh", name);
        product.put("status", "ACTIVE");
        product.put("variants", new java.util.ArrayList<>(List.of(variant)));
        return product;
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
}
