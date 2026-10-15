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
        jdbc.update("DELETE FROM product_variant_supplier");
        jdbc.update("DELETE FROM auxiliary_material_supplier");
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
        createVariants.add(secondVariant);
        String createdBody = mvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(createInput)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productCode").value("PROD-001"))
                .andExpect(jsonPath("$.productName").value("旅行收纳袋"))
                .andExpect(jsonPath("$.variants[0].status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);
        String id = created.get("id").asText();
        String variantId = created.at("/variants/0/id").asText();
        long productVersion = created.get("version").asLong();
        long variantVersion = created.at("/variants/0/version").asLong();

        Map<String, Object> update = product("prod-001", "旅行收纳袋升级版", "sku-001", "Blue");
        @SuppressWarnings("unchecked")
        Map<String, Object> variant = (Map<String, Object>) ((List<?>) update.get("variants")).getFirst();
        variant.put("id", variantId);
        variant.put("version", variantVersion);
        update.put("version", productVersion);
        mvc.perform(put("/api/v1/products/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("旅行收纳袋升级版"));

        mvc.perform(get("/api/v1/products/{id}", id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.skuCount").value(2));
        assertThat(jdbc.queryForObject("SELECT status FROM product_variant WHERE sku_code = 'SKU-002'", String.class))
                .isEqualTo("DISABLED");
        String reloaded = mvc.perform(get("/api/v1/products/{id}", id).header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/products/{id}/disable", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("version", objectMapper.readTree(reloaded).get("version").asLong()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.variants[0].status").value("DRAFT"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE resource_id = ?", String.class, id))
                .containsExactly("PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DISABLE");
    }

    @Test
    void codesRemainUniqueButAttributeCombinationsNoLongerBlockSkuCreation() throws Exception {
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants.length()").value(2));
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

    @Test
    void aggregateSavePersistsSupplierAndMaterialAndRollsBackOnInvalidUnit() throws Exception {
        String token = createUserAndLogin("aggregate-owner", "PRODUCT_MATERIAL");
        String supplierId = java.util.UUID.randomUUID().toString();
        String materialId = java.util.UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO supplier(id,name,phone,status,created_by,updated_by)
                VALUES (?, '聚合供应商', '10086', 'ACTIVE', 'test', 'test')
                """, supplierId);
        jdbc.update("""
                INSERT INTO auxiliary_material
                    (id, material_code, material_code_normalized, name, material_type, unit, status, created_by, updated_by)
                VALUES (?, 'MAT-AGG', 'MAT-AGG', '聚合辅料', 'PACKAGING', '个', 'ACTIVE', 'test', 'test')
                """, materialId);

        Map<String, Object> aggregate = product("PROD-AGG", "聚合产品", "SKU-AGG", "蓝色");
        @SuppressWarnings("unchecked")
        Map<String, Object> variant = (Map<String, Object>) ((List<?>) aggregate.get("variants")).getFirst();
        variant.put("suppliers", List.of(Map.of("supplierId", supplierId, "primary", true)));
        variant.put("materials", List.of(Map.of(
                "materialId", materialId, "standardQuantity", "2.500", "unit", "个",
                "lossRate", "0.050", "enabled", true)));
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(aggregate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].suppliers[0].supplierId").value(supplierId))
                .andExpect(jsonPath("$.variants[0].materials[0].materialId").value(materialId));

        Map<String, Object> invalid = product("PROD-ROLLBACK", "回滚产品", "SKU-ROLLBACK", "白色");
        @SuppressWarnings("unchecked")
        Map<String, Object> invalidVariant = (Map<String, Object>) ((List<?>) invalid.get("variants")).getFirst();
        invalidVariant.put("materials", List.of(Map.of(
                "materialId", materialId, "standardQuantity", "1", "unit", "套",
                "lossRate", "0", "enabled", true)));
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(invalid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_UNIT_MISMATCH"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product WHERE product_code_normalized='PROD-ROLLBACK'", Integer.class))
                .isZero();
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
        Map<String, Object> variant = new java.util.LinkedHashMap<>();
        variant.put("skuCode", skuCode);
        variant.put("variantName", color + " 款");
        variant.put("status", "DRAFT");
        variant.put("packageWeightGrams", "120.500");
        variant.put("suppliers", List.of());
        variant.put("materials", List.of());
        Map<String, Object> product = new java.util.LinkedHashMap<>();
        product.put("productCode", productCode);
        product.put("productName", name);
        product.put("status", "ACTIVE");
        product.put("variants", new java.util.ArrayList<>(List.of(variant)));
        return product;
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
}
