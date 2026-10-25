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
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
        jdbc.update("DELETE FROM customer_product");
        jdbc.update("DELETE FROM product_variant");
        jdbc.update("DELETE FROM product");
        jdbc.update("DELETE FROM iam_password_reset_challenge");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
    }

    @Test
    void customsSavedWithProductAndComplianceEditsShareSameSkuRecord() throws Exception {
        String token=createUserAndLogin("customs-owner","PRODUCT_MATERIAL");
        var input=product("CUSTOMS-P","合成父体","CUSTOMS-S","合成蓝色");
        @SuppressWarnings("unchecked") var variant=(Map<String,Object>)((List<?>)input.get("variants")).getFirst();
        variant.put("customs",customsProfile());
        var created=objectMapper.readTree(mvc.perform(post("/api/v1/products").header("Authorization",bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk())
            .andExpect(jsonPath("$.variants[0].customs.nameEn").value("Synthetic textile pouch"))
            .andExpect(jsonPath("$.variants[0].customs.version").value(1)).andReturn().getResponse().getContentAsString());
        String id=created.path("id").asText(), sku=created.at("/variants/0/id").asText();
        mvc.perform(get("/api/v1/product-customs").param("q","CUSTOMS-S").param("state","DRAFT").header("Authorization",bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].skuName").value("合成蓝色 款"));
        var profile=customsProfile();profile.put("version",1);profile.put("nameEn","Updated synthetic pouch");
        mvc.perform(put("/api/v1/product-customs/"+sku).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(profile)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        mvc.perform(get("/api/v1/products/"+id).header("Authorization",bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].customs.nameEn").value("Updated synthetic pouch"));
        // A stale product editor cannot overwrite a newer compliance edit; product edits roll back too.
        ((com.fasterxml.jackson.databind.node.ObjectNode)created).put("productName","不应保存的合成名称");
        mvc.perform(put("/api/v1/products/"+id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(created.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMS_VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT product_name FROM product WHERE id=?",String.class,id)).isEqualTo("合成父体");
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE id=?",Long.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='PRODUCT_CUSTOMS_UPDATE' AND resource_id=?",Integer.class,sku)).isEqualTo(2);
    }

    @Test
    void customsFailureRollsBackProductAndDefaultCustomerProduct() throws Exception {
        String token=createUserAndLogin("customs-rollback","ADMIN");
        var input=product("CUSTOMS-BAD","合成父体","CUSTOMS-BAD-S","合成");
        @SuppressWarnings("unchecked") var variant=(Map<String,Object>)((List<?>)input.get("variants")).getFirst();
        var profile=customsProfile();profile.put("netWeightKg","2");profile.put("grossWeightKg","1");variant.put("customs",profile);
        mvc.perform(post("/api/v1/products").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CUSTOMS_WEIGHT_INVALID"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_product",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action IN ('PRODUCT_CREATE','CUSTOMER_PRODUCT_CREATE','PRODUCT_CUSTOMS_UPDATE')",Integer.class)).isZero();
    }

    @Test
    void customsMissingDraftReviewValidationAndPermissions() throws Exception {
        String token=createUserAndLogin("customs-validation","ADMIN");
        var created=objectMapper.readTree(mvc.perform(post("/api/v1/products").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content(json(product("CUSTOMS-EMPTY","合成父体","CUSTOMS-EMPTY-S","合成")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String sku=created.at("/variants/0/id").asText(), path="/api/v1/product-customs/"+sku;
        mvc.perform(get("/api/v1/product-customs").param("state","MISSING").header("Authorization",bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        for(var change:List.of(Map.of("usCode","123"),Map.of("originCountry","china"),Map.of("chinaCode","123456789A"),Map.of("netWeightKg","0"),Map.of("state","REVIEWED"))) {
            var profile=customsProfile();profile.putAll(change);
            mvc.perform(put(path).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(profile))).andExpect(status().isBadRequest());
        }
        var reviewed=customsProfile();reviewed.putAll(Map.of("state","REVIEWED","nameZh","合成袋","composition","合成材质","usage","合成收纳用途","originCountry","CN","unit","个","chinaCode","0000000000","declarationElements","合成申报要素，非真实归类"));
        mvc.perform(put(path).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(reviewed))).andExpect(status().isOk());
        String reader=createUserAndLogin("customs-reader","SELECTION");
        mvc.perform(get("/api/v1/product-customs").param("state","REVIEWED").header("Authorization",bearer(reader))).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(put(path).header("Authorization",bearer(reader)).contentType(MediaType.APPLICATION_JSON).content(json(reviewed))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/product-customs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/product-customs").param("page","-1").header("Authorization",bearer(token))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/product-customs/missing").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(customsProfile()))).andExpect(status().isNotFound());
    }
    private Map<String,Object> customsProfile() { return new java.util.HashMap<>(Map.of("nameEn","Synthetic textile pouch","state","DRAFT","version",0)); }

    @Test
    void createsEachSkuForCurrentDefaultCustomerWithoutInventingPrices() throws Exception {
        String token = createUserAndLogin("default-customer-owner", "PRODUCT_MATERIAL");
        String oldDefault = jdbc.queryForObject("SELECT default_customer_id FROM customer_preferences WHERE id=1", String.class);
        String customer = java.util.UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer(id,name,status,created_by,updated_by) VALUES(?,'合成默认客户','ACTIVE','test','test')", customer);
        jdbc.update("UPDATE customer_preferences SET default_customer_id=? WHERE id=1", customer);
        try {
            var input = product("AUTO-P", "合成父体", "AUTO-A", "合成蓝色");
            @SuppressWarnings("unchecked")
            var variants = (List<Map<String,Object>>) input.get("variants");
            var second = new java.util.LinkedHashMap<>(variants.getFirst());
            second.put("skuCode", "AUTO-B"); second.put("variantName", "合成红色款"); second.put("status", "ACTIVE");
            variants.add(second);
            JsonNode created = objectMapper.readTree(mvc.perform(post("/api/v1/products")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            String id = created.path("id").asText();
            assertThat(jdbc.queryForList("SELECT customer_id FROM customer_product WHERE product_id=?", String.class, id))
                .containsExactly(customer, customer);
            assertThat(jdbc.queryForList("SELECT product_name FROM customer_product WHERE product_id=?", String.class, id))
                .containsExactlyInAnyOrder("合成蓝色 款", "合成红色款");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_product WHERE product_id=? AND supply_price IS NULL AND price_unit='套' AND customer_product_code='' AND requirements='' AND status='ACTIVE'", Integer.class, id)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='CUSTOMER_PRODUCT_CREATE'", Integer.class)).isEqualTo(2);
            // A second submission cannot duplicate associations; editing never transfers ownership.
            mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isConflict());
            jdbc.update("UPDATE customer_preferences SET default_customer_id=? WHERE id=1", oldDefault);
            mvc.perform(put("/api/v1/products/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(created.toString())).andExpect(status().isOk());
            assertThat(jdbc.queryForList("SELECT customer_id FROM customer_product WHERE product_id=?", String.class, id))
                .containsExactly(customer, customer);
        } finally {
            jdbc.update("UPDATE customer_preferences SET default_customer_id=? WHERE id=1", oldDefault);
        }
    }

    @Test
    void unavailableDefaultCustomerRollsBackProductSkusAndAudit() throws Exception {
        String token = createUserAndLogin("default-rollback-owner", "PRODUCT_MATERIAL");
        String customer = jdbc.queryForObject("SELECT default_customer_id FROM customer_preferences WHERE id=1", String.class);
        try {
            for (boolean missing : List.of(true, false)) {
                jdbc.update("UPDATE customer_preferences SET default_customer_id=? WHERE id=1", missing ? null : customer);
                jdbc.update("UPDATE customer SET status='DISABLED' WHERE id=?", customer);
                mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                    .contentType(MediaType.APPLICATION_JSON).content(json(product("ROLL-P", "合成回滚产品", "ROLL-S", "合成"))))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEFAULT_CUSTOMER_REQUIRED"));
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_product", Integer.class)).isZero();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action IN ('PRODUCT_CREATE','CUSTOMER_PRODUCT_CREATE')", Integer.class)).isZero();
            }
        } finally {
            jdbc.update("UPDATE customer SET status='ACTIVE' WHERE id=?", customer);
            jdbc.update("UPDATE customer_preferences SET default_customer_id=? WHERE id=1", customer);
        }
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
    void skuImageUrlMustBeHttpsAndCannotBeCombinedWithAnUploadedImage() throws Exception {
        String token = createUserAndLogin("product-image-owner", "PRODUCT_MATERIAL");
        Map<String, Object> valid = product("PROD-IMAGE", "图片产品", "SKU-IMAGE", "标准");
        @SuppressWarnings("unchecked")
        Map<String, Object> validVariant = (Map<String, Object>) ((List<?>) valid.get("variants")).getFirst();
        validVariant.put("imageUrl", "https://images.example.test/sku.png");
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(valid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].imageUrl").value("https://images.example.test/sku.png"));
        assertThat(jdbc.queryForObject(
                "SELECT main_image_url FROM product_variant WHERE sku_code='SKU-IMAGE'", String.class))
                .isEqualTo("https://images.example.test/sku.png");

        Map<String, Object> insecure = product("PROD-HTTP", "不安全图片", "SKU-HTTP", "标准");
        @SuppressWarnings("unchecked")
        Map<String, Object> insecureVariant = (Map<String, Object>) ((List<?>) insecure.get("variants")).getFirst();
        insecureVariant.put("imageUrl", "http://images.example.test/sku.png");
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(insecure)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_URL_INVALID"));

        Map<String, Object> conflicting = product("PROD-CONFLICT", "冲突图片", "SKU-CONFLICT", "标准");
        @SuppressWarnings("unchecked")
        Map<String, Object> conflictingVariant = (Map<String, Object>) ((List<?>) conflicting.get("variants")).getFirst();
        conflictingVariant.put("imageUrl", "https://images.example.test/sku.png");
        conflictingVariant.put("imageAssetId", java.util.UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(conflicting)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_IMAGE_SOURCE_CONFLICT"));
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

    @Test
    void skuListFiltersStructuredDimensionsAndBatchUpdateAreSupported() throws Exception {
        String token = createUserAndLogin("sku-list-owner", "PRODUCT_MATERIAL");
        Map<String, Object> input = product("PROD-SKU-LIST", "收纳产品", "SKU-LIST-001", "蓝色");
        @SuppressWarnings("unchecked")
        Map<String, Object> variant = (Map<String, Object>) ((List<?>) input.get("variants")).getFirst();
        variant.put("itemLengthCm", "12.500");
        variant.put("itemWidthCm", "8.000");
        variant.put("itemHeightCm", "3.250");
        String body = mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variants[0].itemLengthCm").value(12.5))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);

        mvc.perform(get("/api/v1/products/skus").header("Authorization", bearer(token))
                        .param("field", "SKU").param("q", "SKU-LIST-001").param("status", "DRAFT")
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].variant.skuCode").value("SKU-LIST-001"))
                .andExpect(jsonPath("$.items[0].businessAvailable").value(false));

        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        patch.put("productId", created.get("id").asText());
        patch.put("variantId", created.at("/variants/0/id").asText());
        patch.put("productVersion", created.get("version").asLong());
        patch.put("variantVersion", created.at("/variants/0/version").asLong());
        patch.put("statusSelected", true);
        patch.put("status", "ACTIVE");
        patch.put("itemDimensionsSelected", false);
        patch.put("packageDimensionsSelected", true);
        patch.put("packageLengthCm", "20.000");
        patch.put("packageWidthCm", "10.000");
        patch.put("packageHeightCm", "4.000");
        patch.put("packageWeightSelected", true);
        patch.put("packageWeightGrams", "250.500");
        mvc.perform(post("/api/v1/products/skus/batch").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("items", List.of(patch)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].variants[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].variants[0].packageLengthCm").value(20.0))
                .andExpect(jsonPath("$[0].variants[0].packageWeightGrams").value(250.5));

        byte[] export = mvc.perform(get("/api/v1/products/skus/export").header("Authorization", bearer(token))
                        .param("field", "SKU").param("q", "SKU-LIST-001"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(export))) {
            assertThat(workbook.getSheet("产品SKU").getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("SKU-LIST-001");
            assertThat(workbook.getSheet("产品SKU").getRow(1).getCell(12).getNumericCellValue())
                    .isEqualTo(4.0);
        }
        mvc.perform(get("/api/v1/products/{id}/history", created.get("id").asText())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").exists());
    }

    @Test
    void batchUpdateRejectsPartialDimensionsAndRollsBack() throws Exception {
        String token = createUserAndLogin("sku-batch-owner", "PRODUCT_MATERIAL");
        String body = mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(product("PROD-BATCH", "批量产品", "SKU-BATCH", "标准"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(body);
        Map<String, Object> patch = new java.util.LinkedHashMap<>();
        patch.put("productId", created.get("id").asText());
        patch.put("variantId", created.at("/variants/0/id").asText());
        patch.put("productVersion", created.get("version").asLong());
        patch.put("variantVersion", created.at("/variants/0/version").asLong());
        patch.put("statusSelected", false);
        patch.put("itemDimensionsSelected", true);
        patch.put("itemLengthCm", "5.000");
        patch.put("packageDimensionsSelected", false);
        patch.put("packageWeightSelected", false);
        mvc.perform(post("/api/v1/products/skus/batch").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("items", List.of(patch)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_FIELD_INVALID"));
        assertThat(jdbc.queryForObject(
                "SELECT item_length_cm FROM product_variant WHERE sku_code='SKU-BATCH'", BigDecimal.class))
                .isNull();
    }

    @Test
    void skuDocumentsRoundTripWithKindIsolationOwnershipAndPermissions() throws Exception {
        String token = createUserAndLogin("document-owner", "PRODUCT_MATERIAL");
        String reader = createUserAndLogin("document-reader", "SELECTION");
        JsonNode created = objectMapper.readTree(mvc.perform(post("/api/v1/products")
            .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content(json(product("DOC-P", "文件测试产品", "DOC-SKU", "蓝色"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String path = "/api/v1/products/" + created.get("id").asText() + "/skus/" + created.at("/variants/0/id").asText() + "/documents";
        byte[] content = "%PDF-1.4\nSynthetic manual".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = new org.springframework.mock.web.MockMultipartFile("file", "说明书.pdf", "application/pdf", content);
        JsonNode uploaded = objectMapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(path)
            .file(file).param("kind", "MANUAL").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fileName").value("说明书.pdf"))
            .andReturn().getResponse().getContentAsString());
        String download = path + "/" + uploaded.get("id").asText() + "/download";
        mvc.perform(get(path).param("kind", "MANUAL").header("Authorization", bearer(reader)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(path).param("kind", "LABEL").header("Authorization", bearer(reader)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        var result = mvc.perform(get(download).header("Authorization", bearer(reader)))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(result.getContentAsByteArray()).isEqualTo(content);
        assertThat(result.getHeader("Content-Disposition")).contains("attachment");
        assertThat(result.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(path)
            .file(file).param("kind", "LABEL").header("Authorization", bearer(reader))).andExpect(status().isForbidden());
        mvc.perform(get(download)).andExpect(status().isUnauthorized());
        mvc.perform(get(download.replace(created.get("id").asText(), "wrong-product"))
            .header("Authorization", bearer(token))).andExpect(status().isNotFound());
        mvc.perform(get(path + "/missing/download").header("Authorization", bearer(token))).andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(path)
            .file(new org.springframework.mock.web.MockMultipartFile("file", "label.zpl", "text/plain", "^XA^FO10,10^FDTEST^FS^XZ".getBytes()))
            .param("kind", "LABEL").header("Authorization", bearer(token))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='SKU_DOCUMENT_UPLOAD'", Integer.class)).isEqualTo(2);
        // Editing the aggregate must preserve the SKU's stored documents.
        mvc.perform(put("/api/v1/products/" + created.get("id").asText()).header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(created.toString())).andExpect(status().isOk());
        mvc.perform(get(download).header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    @Test
    void skuDocumentsRejectInvalidSizeFormatNameAndKind() throws Exception {
        String token = createUserAndLogin("document-validation", "PRODUCT_MATERIAL");
        JsonNode created = objectMapper.readTree(mvc.perform(post("/api/v1/products")
            .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content(json(product("DOC-V", "文件校验", "DOC-V-SKU", "标准"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String path = "/api/v1/products/" + created.get("id").asText() + "/skus/" + created.at("/variants/0/id").asText() + "/documents";
        for (var file : List.of(
            new org.springframework.mock.web.MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]),
            new org.springframework.mock.web.MockMultipartFile("file", "large.pdf", "application/pdf", new byte[10 * 1024 * 1024 + 1]),
            new org.springframework.mock.web.MockMultipartFile("file", "invalid.pdf", "application/pdf", "not pdf".getBytes()),
            new org.springframework.mock.web.MockMultipartFile("file", "invalid.docx", "application/zip", "PK fake".getBytes()),
            new org.springframework.mock.web.MockMultipartFile("file", "../manual.txt", "text/plain", "text".getBytes()),
            new org.springframework.mock.web.MockMultipartFile("file", "script.html", "text/html", "<script/>".getBytes())
        )) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(path)
                .file(file).param("kind", "MANUAL").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get(path).param("kind", "INVALID").header("Authorization", bearer(token))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sku_document", Integer.class)).isZero();
    }

    @Test
    void keywordAndTypeFiltersMatchSpecificationsForListAndExport() throws Exception {
        String token = createUserAndLogin("keyword-owner", "PRODUCT_MATERIAL");
        Map<String, Object> input = product("KEY-P", "收纳箱", "KEY-SKU", "标准");
        input.put("productType", "家居");
        @SuppressWarnings("unchecked")
        Map<String, Object> variant = (Map<String, Object>) ((List<?>) input.get("variants")).getFirst();
        variant.put("itemDimensions", "40x30x20cm");
        mvc.perform(post("/api/v1/products").header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/skus").param("field", "KEYWORD").param("exact", "false").param("q", "40x30")
            .param("productType", "家居").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/v1/products/skus").param("productType", "电子").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        byte[] exported = mvc.perform(get("/api/v1/products/skus/export").param("field", "KEYWORD").param("exact", "false")
            .param("q", "40x30").param("productType", "家居").header("Authorization", bearer(token)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(exported))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(2);
        }
    }

    @Test
    void parentImageUploadReplaceRemoveAndBatchPreserveSkuImages() throws Exception {
        String token=createUserAndLogin("parent-image-owner","PRODUCT_MATERIAL");
        String parent=uploadCatalogImage(token,"PRODUCT_MAIN"), sku=uploadCatalogImage(token,"SKU_MAIN");
        var input=product("PARENT-IMAGE","合成父体","PARENT-IMAGE-S","合成子体");
        input.put("imageAssetId",parent);
        @SuppressWarnings("unchecked") var variant=(Map<String,Object>)((List<?>)input.get("variants")).getFirst();
        variant.put("imageAssetId",sku);
        var created=objectMapper.readTree(mvc.perform(post("/api/v1/products").header("Authorization",bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk())
            .andExpect(jsonPath("$.imageAssetId").value(parent)).andExpect(jsonPath("$.variants[0].imageAssetId").value(sku))
            .andReturn().getResponse().getContentAsString());
        String id=created.path("id").asText(), variantId=created.at("/variants/0/id").asText();
        mvc.perform(get("/api/v1/catalog-images/"+parent+"/original").header("Authorization",bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products").header("Authorization",bearer(token))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].imageAssetId").value(parent));
        mvc.perform(post("/api/v1/products/skus/batch").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("items",List.of(Map.of("productId",id,"variantId",variantId,"productVersion",0,
                "variantVersion",0,"statusSelected",true,"status","ACTIVE")))))).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].imageAssetId").value(parent));
        var current=(com.fasterxml.jackson.databind.node.ObjectNode)objectMapper.readTree(mvc.perform(get("/api/v1/products/"+id)
            .header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String replacement=uploadCatalogImage(token,"PRODUCT_MAIN"); current.put("imageAssetId",replacement);
        var updated=(com.fasterxml.jackson.databind.node.ObjectNode)objectMapper.readTree(mvc.perform(put("/api/v1/products/"+id)
            .header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(current.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.imageAssetId").value(replacement))
            .andExpect(jsonPath("$.variants[0].imageAssetId").value(sku)).andReturn().getResponse().getContentAsString());
        assertThat(jdbc.queryForObject("SELECT status FROM catalog_image_asset WHERE id=?",String.class,parent)).isEqualTo("PENDING");
        mvc.perform(put("/api/v1/products/"+id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(current.toString()))
            .andExpect(status().isConflict());
        updated.putNull("imageAssetId");
        mvc.perform(put("/api/v1/products/"+id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(updated.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.imageAssetId").isEmpty()).andExpect(jsonPath("$.variants[0].imageAssetId").value(sku));
        assertThat(jdbc.queryForObject("SELECT status FROM catalog_image_asset WHERE id=?",String.class,replacement)).isEqualTo("PENDING");
    }

    @Test
    void parentImagesRejectWrongPurposeOtherOwnerAndUnauthorizedUpload() throws Exception {
        String token=createUserAndLogin("parent-valid-owner","PRODUCT_MATERIAL");
        String other=createUserAndLogin("parent-other-owner","PRODUCT_MATERIAL");
        var input=product("BAD-PARENT","合成父体","BAD-PARENT-S","合成");
        input.put("imageAssetId",uploadCatalogImage(token,"SKU_MAIN"));
        mvc.perform(post("/api/v1/products").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CATALOG_IMAGE_PURPOSE_INVALID"));
        input.put("imageAssetId",uploadCatalogImage(other,"PRODUCT_MAIN"));
        mvc.perform(post("/api/v1/products").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_code='BAD-PARENT'",Integer.class)).isZero();
        String reader=createUserAndLogin("parent-reader","SELECTION");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/catalog-images")
            .file(new org.springframework.mock.web.MockMultipartFile("file","invalid.png","image/png",new byte[]{1,2,3}))
            .param("purpose","PRODUCT_MAIN").header("Authorization",bearer(reader))).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/catalog-images")
            .file(new org.springframework.mock.web.MockMultipartFile("file","invalid.png","image/png",new byte[]{1,2,3}))
            .param("purpose","PRODUCT_MAIN").header("Authorization",bearer(token))).andExpect(status().isBadRequest());
    }

    @Test
    void multilineNameStyleIsPersistentPermissionCheckedAndVersioned() throws Exception {
        String token = createUserAndLogin("style-owner", "PRODUCT_MATERIAL");
        var input = objectMapper.valueToTree(product("STYLE-P", "合成父体", "STYLE-S", "合成"));
        var variant = (com.fasterxml.jackson.databind.node.ObjectNode) input.path("variants").get(0);
        variant.put("variantName", "合成名称\n第二行");
        JsonNode created = objectMapper.readTree(mvc.perform(post("/api/v1/products")
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].nameHighlighted").value(false))
                .andReturn().getResponse().getContentAsString());
        String id = created.path("id").asText();
        ((com.fasterxml.jackson.databind.node.ObjectNode) created.path("variants").get(0)).put("nameHighlighted", true);
        String reader = createUserAndLogin("style-reader", "SELECTION");
        mvc.perform(put("/api/v1/products/" + id).header("Authorization", bearer(reader))
                .contentType(MediaType.APPLICATION_JSON).content(created.toString())).andExpect(status().isForbidden());
        JsonNode updated = objectMapper.readTree(mvc.perform(put("/api/v1/products/" + id)
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(created.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].nameHighlighted").value(true))
                .andExpect(jsonPath("$.variants[0].variantName").value("合成名称\n第二行"))
                .andReturn().getResponse().getContentAsString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) created.path("variants").get(0)).put("nameHighlighted", false);
        mvc.perform(put("/api/v1/products/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(created.toString())).andExpect(status().isConflict());
        ((com.fasterxml.jackson.databind.node.ObjectNode) updated.path("variants").get(0)).remove("nameHighlighted");
        JsonNode preserved = objectMapper.readTree(mvc.perform(put("/api/v1/products/" + id)
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(updated.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].nameHighlighted").value(true))
                .andReturn().getResponse().getContentAsString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) preserved.path("variants").get(0)).put("nameHighlighted", false);
        mvc.perform(put("/api/v1/products/" + id).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(preserved.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].nameHighlighted").value(false));
        mvc.perform(get("/api/v1/products/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.variants[0].variantName").value("合成名称\n第二行"))
                .andExpect(jsonPath("$.variants[0].nameHighlighted").value(false));
    }

    private String uploadCatalogImage(String token,String purpose) throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        return objectMapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/catalog-images")
            .file(new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray()))
            .param("purpose",purpose).header("Authorization",bearer(token))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).path("id").asText();
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
