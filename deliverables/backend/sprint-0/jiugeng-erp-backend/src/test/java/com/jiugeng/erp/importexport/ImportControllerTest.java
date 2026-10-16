package com.jiugeng.erp.importexport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportControllerTest {
    private static final String PASSWORD="Import-Test-42";
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    @Autowired ImportTemplateRegistry templates;

    @BeforeEach void reset(){
        jdbc.update("DELETE FROM import_operation_idempotency"); jdbc.update("DELETE FROM import_preview_row");
        jdbc.update("DELETE FROM import_issue"); jdbc.update("DELETE FROM import_batch");
        jdbc.update("DELETE FROM inventory_transaction"); jdbc.update("DELETE FROM inventory_balance");
        jdbc.update("DELETE FROM product_auxiliary_material"); jdbc.update("DELETE FROM variant_attribute");
        jdbc.update("DELETE FROM product_variant"); jdbc.update("DELETE FROM product"); jdbc.update("DELETE FROM auxiliary_material");
        jdbc.update("DELETE FROM iam_password_reset_challenge"); jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session"); jdbc.update("DELETE FROM iam_user");
    }

    @Test void uploadValidatesPreviewsAndEnforcesIdempotencyAndProductCommit() throws Exception {
        int productCount=jdbc.queryForObject("SELECT COUNT(*) FROM product",Integer.class);
        String token=user("import-owner","PRODUCT_MATERIAL"); byte[] file=templates.bytes(ImportTypes.ImportType.PRODUCTS);
        String first=upload(token,"upload-1","PRODUCTS",file,202);
        String id=mapper.readTree(first).at("/batch/id").asText();
        assertThat(mapper.readTree(first).at("/batch/status").asText()).isEqualTo("QUEUED");
        mvc.perform(get("/api/v1/import-batches/{id}",id).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.commitAllowed").value(true));
        mvc.perform(get("/api/v1/import-batches/{id}/preview",id).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.inputRows").value(0));
        String replay=upload(token,"upload-1","PRODUCTS",file,202);
        assertThat(mapper.readTree(replay).at("/batch/id").asText()).isEqualTo(id);
        byte[] changed=file.clone(); changed[changed.length-1]^=1;
        upload(token,"upload-1","PRODUCTS",changed,409);
        String batch=mvc.perform(get("/api/v1/import-batches/{id}",id).header("Authorization",bearer(token)))
                .andReturn().getResponse().getContentAsString();
        String preview=mapper.readTree(batch).get("previewVersion").asText(); String hash=mapper.readTree(batch).get("fileHash").asText();
        mvc.perform(post("/api/v1/import-batches/{id}/commit",id).header("Authorization",bearer(token))
                        .header("Idempotency-Key","commit-1").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("previewVersion",preview,"fileHash",hash,"warningsAccepted",true))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(post("/api/v1/import-batches/{id}/commit",id).header("Authorization",bearer(token))
                        .header("Idempotency-Key","commit-1").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("previewVersion",preview,"fileHash",hash,"warningsAccepted",true))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(post("/api/v1/import-batches/{id}/commit",id).header("Authorization",bearer(token))
                        .header("Idempotency-Key","commit-1").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("previewVersion",preview,"fileHash",hash,"warningsAccepted",false))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product",Integer.class)).isEqualTo(productCount);
    }

    @Test void servesExactCatalogV2TemplatesAndKeepsOtherTypesAtV1() throws Exception {
        assertThat(templates.definition(ImportTypes.ImportType.PRODUCTS).version()).isEqualTo("2.0");
        assertThat(templates.definition(ImportTypes.ImportType.VARIANTS).version()).isEqualTo("2.1");
        assertThat(templates.definition(ImportTypes.ImportType.AUXILIARY_MATERIALS).version()).isEqualTo("1.0");
        assertThat(templates.definition(ImportTypes.ImportType.PRODUCT_AUXILIARY_MATERIALS).version()).isEqualTo("1.0");
        assertThat(templates.definition(ImportTypes.ImportType.AUXILIARY_MATERIAL_OPENING_INVENTORY).version()).isEqualTo("1.0");
        assertThat(templates.supports(ImportTypes.ImportType.PRODUCTS,"1.0")).isFalse();
        assertThat(templates.supports(ImportTypes.ImportType.VARIANTS,"2.0")).isTrue();
        assertThat(templates.supports(ImportTypes.ImportType.AUXILIARY_MATERIALS,"1.0")).isTrue();
        try (var products = new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.PRODUCTS)));
             var variants = new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.VARIANTS)))) {
            assertThat(headers(products, "导入模板")).containsExactly(
                    "product_code", "product_name", "product_type", "description", "status");
            assertThat(headers(variants, "variants_导入模板")).containsExactly(
                    "sku_code", "product_code", "variant_name", "item_dimensions", "package_dimensions",
                    "item_length_cm", "item_width_cm", "item_height_cm", "package_length_cm",
                    "package_width_cm", "package_height_cm", "package_weight_grams", "status");
            assertThat(variants.getSheet("attributes_导入模板")).isNull();
            assertThat(meta(products, "templateVersion")).isEqualTo("2.0");
            assertThat(meta(variants, "templateVersion")).isEqualTo("2.1");
        }
    }

    @Test void productAndVariantHandlersUpsertAtomicallyAndReportCounts() throws Exception {
        String token=user("import-owner","PRODUCT_MATERIAL");
        String productBody=upload(token,"products-1","PRODUCTS",productRows("IMP-P001","导入产品"),202);
        String productId=mapper.readTree(productBody).at("/batch/id").asText();
        commit(token,productId,"products-commit-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/import-batches/{id}/result",productId).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdRows").value(1))
                .andExpect(jsonPath("$.writtenRows").value(1));
        assertThat(jdbc.queryForObject("SELECT product_name FROM product WHERE product_code_normalized='IMP-P001'",String.class)).isEqualTo("导入产品");

        String variantBody=upload(token,"variants-1","VARIANTS",variantRows("IMP-SKU001","IMP-P001","Black"),202);
        String variantBatch=mapper.readTree(variantBody).at("/batch/id").asText();
        commit(token,variantBatch,"variants-commit-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE sku_code_normalized='IMP-SKU001'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT package_weight_grams FROM product_variant WHERE sku_code_normalized='IMP-SKU001'",java.math.BigDecimal.class))
                .isEqualByComparingTo("500");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM variant_attribute WHERE variant_id=(SELECT id FROM product_variant WHERE sku_code_normalized='IMP-SKU001')",Integer.class)).isZero();

        String replayBody=upload(token,"variants-2","VARIANTS",variantRows("IMP-SKU001","IMP-P001","Black"),202);
        String replayBatch=mapper.readTree(replayBody).at("/batch/id").asText();
        commit(token,replayBatch,"variants-commit-2").andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/import-batches/{id}/result",replayBatch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchangedRows").value(1));
    }

    @Test void variantV21PersistsStructuredDimensionsAndV20UpdatePreservesThem() throws Exception {
        String token=user("variant-compat-owner","PRODUCT_MATERIAL");
        seedProductAndVariants("DIM-P");
        String batch=mapper.readTree(upload(token,"variant-v21","VARIANTS",structuredVariantRows("DIM-SKU","DIM-P"),202)).at("/batch/id").asText();
        commit(token,batch,"variant-v21-commit").andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT item_length_cm FROM product_variant WHERE sku_code_normalized='DIM-SKU'",java.math.BigDecimal.class)).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT package_height_cm FROM product_variant WHERE sku_code_normalized='DIM-SKU'",java.math.BigDecimal.class)).isEqualByComparingTo("7");

        String legacy=uploadVersion(token,"variant-v20","VARIANTS","2.0",legacyVariantRows("DIM-SKU","DIM-P"),202);
        String legacyBatch=mapper.readTree(legacy).at("/batch/id").asText();
        commit(token,legacyBatch,"variant-v20-commit").andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT item_length_cm FROM product_variant WHERE sku_code_normalized='DIM-SKU'",java.math.BigDecimal.class)).isEqualByComparingTo("10");
    }

    @Test void lateReferenceChangeFailsWholeVariantBatchWithoutPartialWrites() throws Exception {
        String token=user("import-owner","PRODUCT_MATERIAL");
        String productBatch=mapper.readTree(upload(token,"late-product","PRODUCTS",productRows("LATE-P","稍后删除"),202)).at("/batch/id").asText();
        commit(token,productBatch,"late-product-commit").andExpect(status().isAccepted());
        String variantBatch=mapper.readTree(upload(token,"late-variant","VARIANTS",variantRows("LATE-SKU","LATE-P","One"),202)).at("/batch/id").asText();
        jdbc.update("DELETE FROM product WHERE product_code_normalized='LATE-P'");
        commit(token,variantBatch,"late-variant-commit").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_DATA_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT status FROM import_batch WHERE id=?",String.class,variantBatch)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE sku_code_normalized='LATE-SKU'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT outcome FROM audit_event WHERE action='IMPORT_COMMIT' AND resource_id=?",String.class,variantBatch)).isEqualTo("FAILURE");
    }

    @Test void importsDifferentSkusWithoutDynamicAttributesAndSupportsDraft() throws Exception {
        String token=user("attribute-free-owner","PRODUCT_MATERIAL");
        String productBatch=mapper.readTree(upload(token,"attribute-free-product","PRODUCTS",
                productRows("ATTRIBUTE-FREE-P","无动态属性产品"),202)).at("/batch/id").asText();
        commit(token,productBatch,"attribute-free-product-commit").andExpect(status().isAccepted());
        String variantBatch=mapper.readTree(upload(token,"attribute-free-variants","VARIANTS",
                variantRowsWithoutAttributes("ATTRIBUTE-FREE-P"),202)).at("/batch/id").asText();
        commit(token,variantBatch,"attribute-free-variants-commit").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE product_id=(SELECT id FROM product WHERE product_code_normalized='ATTRIBUTE-FREE-P')",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM product_variant WHERE sku_code_normalized='ATTRIBUTE-FREE-SKU-2'",String.class)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM variant_attribute",Integer.class)).isZero();
    }

    @Test void acceptsAndCommitsOneThousandProductRowsWithinGate() {
        long started = System.nanoTime();
        assertTimeout(Duration.ofMinutes(5), () -> {
            String token=user("bulk-owner","PRODUCT_MATERIAL");
            String batch=mapper.readTree(upload(token,"bulk-products","PRODUCTS",productRows(1000),202)).at("/batch/id").asText();
            commit(token,batch,"bulk-products-commit").andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_code_normalized LIKE 'BULK-P-%'",Integer.class)).isEqualTo(1000);
        });
        System.out.printf("PERF import-products-1000 elapsed_ms=%.3f threshold_ms=300000%n",
                (System.nanoTime() - started) / 1_000_000.0);
    }

    @Test void materialAndRelationHandlersUpsertDependenciesAtomicallyAndReportCounts() throws Exception {
        String token=user("material-import-owner","PRODUCT_MATERIAL");
        seedProductAndVariants("MAT-P001", "MAT-SKU001", "MAT-SKU002");

        String materialBatch=mapper.readTree(upload(token,"materials-1","AUXILIARY_MATERIALS",
                materialRows(new String[][]{{"MAT-IMP001","包装盒","BOX","PCS","ACTIVE"}}),202))
                .at("/batch/id").asText();
        commit(token,materialBatch,"materials-commit-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/import-batches/{id}/result",materialBatch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdRows").value(1));
        String materialUpdate=mapper.readTree(upload(token,"materials-2","AUXILIARY_MATERIALS",
                materialRows(new String[][]{{"mat-imp001","加厚包装盒","BOX","PCS","ACTIVE"}}),202))
                .at("/batch/id").asText();
        commit(token,materialUpdate,"materials-commit-2").andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/import-batches/{id}/result",materialUpdate).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedRows").value(1));
        assertThat(jdbc.queryForObject("SELECT material_code FROM auxiliary_material WHERE material_code_normalized='MAT-IMP001'",String.class))
                .isEqualTo("MAT-IMP001");

        String relationBatch=mapper.readTree(upload(token,"relations-1","PRODUCT_AUXILIARY_MATERIALS",
                relationRows(new String[][]{{"MAT-SKU001","MAT-IMP001","2.0000","PCS","0.0500","TRUE"},
                        {"MAT-SKU002","MAT-IMP001","1.0000","PCS","0.0000","FALSE"}}),202))
                .at("/batch/id").asText();
        commit(token,relationBatch,"relations-commit-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/import-batches/{id}/result",relationBatch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdRows").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_auxiliary_material",Integer.class)).isEqualTo(2);

        String replay=mapper.readTree(upload(token,"relations-2","PRODUCT_AUXILIARY_MATERIALS",
                relationRows(new String[][]{{"mat-sku001","mat-imp001","2","PCS","0.05","TRUE"},
                        {"MAT-SKU002","MAT-IMP001","1","PCS","0","FALSE"}}),202)).at("/batch/id").asText();
        commit(token,replay,"relations-commit-2").andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/import-batches/{id}/result",replay).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchangedRows").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='MATERIAL_IMPORT_CREATE'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='PRODUCT_MATERIAL_IMPORT_CREATE'",Integer.class)).isEqualTo(2);
    }

    @Test void relationUploadRejectsMissingDependenciesDuplicatesAndUnitMismatch() throws Exception {
        String token=user("material-import-owner","PRODUCT_MATERIAL");
        seedProductAndVariants("DEP-P001", "DEP-SKU001", "DEP-SKU002");
        jdbc.update("""
                INSERT INTO auxiliary_material (id,material_code,material_code_normalized,name,material_type,unit,status,created_by,updated_by)
                VALUES (?,?,?,?,?,'PCS','ACTIVE','system','system')
                """,UUID.randomUUID().toString(),"DEP-MAT001","DEP-MAT001","依赖辅料","BOX");
        String batch=mapper.readTree(upload(token,"relations-invalid","PRODUCT_AUXILIARY_MATERIALS",
                relationRows(new String[][]{{"UNKNOWN-SKU","DEP-MAT001","1","PCS","0","TRUE"},
                        {"DEP-SKU001","UNKNOWN-MAT","1","PCS","0","TRUE"},
                        {"DEP-SKU002","DEP-MAT001","1","KG","0","TRUE"},
                        {"DEP-SKU002","dep-mat001","1","PCS","0","TRUE"}}),202)).at("/batch/id").asText();
        mvc.perform(get("/api/v1/import-batches/{id}",batch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commitAllowed").value(false));
        String issues=mvc.perform(get("/api/v1/import-batches/{id}/issues",batch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(issues).findValuesAsText("code"))
                .contains("SKU_REFERENCE_INVALID","MATERIAL_REFERENCE_INVALID","MATERIAL_UNIT_MISMATCH",
                        "MATERIAL_RELATION_DUPLICATED_IN_FILE");
    }

    @Test void lateMaterialDisableFailsWholeRelationBatchWithoutPartialWrites() throws Exception {
        String token=user("material-import-owner","PRODUCT_MATERIAL");
        seedProductAndVariants("LATE-MAT-P", "LATE-MAT-SKU1", "LATE-MAT-SKU2");
        String materialBatch=mapper.readTree(upload(token,"late-material","AUXILIARY_MATERIALS",
                materialRows(new String[][]{{"LATE-MAT","稍后停用","BOX","PCS","ACTIVE"}}),202)).at("/batch/id").asText();
        commit(token,materialBatch,"late-material-commit").andExpect(status().isAccepted());
        String relationBatch=mapper.readTree(upload(token,"late-relations","PRODUCT_AUXILIARY_MATERIALS",
                relationRows(new String[][]{{"LATE-MAT-SKU1","LATE-MAT","1","PCS","0","TRUE"},
                        {"LATE-MAT-SKU2","LATE-MAT","1","PCS","0","TRUE"}}),202)).at("/batch/id").asText();
        jdbc.update("UPDATE auxiliary_material SET status='DISABLED' WHERE material_code_normalized='LATE-MAT'");
        commit(token,relationBatch,"late-relations-commit").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_DATA_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_auxiliary_material",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM import_batch WHERE id=?",String.class,relationBatch)).isEqualTo("FAILED");
    }

    @Test void acceptsAndCommitsOneThousandMaterialRowsWithinGate() {
        assertTimeout(Duration.ofSeconds(30), () -> {
            String token=user("bulk-material-owner","PRODUCT_MATERIAL");
            String[][] rows=new String[1000][];
            for(int index=0;index<rows.length;index++) rows[index]=new String[]{"BULK-MAT-"+(index+1),"批量辅料 "+(index+1),"BOX","PCS","ACTIVE"};
            String batch=mapper.readTree(upload(token,"bulk-materials","AUXILIARY_MATERIALS",materialRows(rows),202)).at("/batch/id").asText();
            commit(token,batch,"bulk-materials-commit").andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auxiliary_material WHERE material_code_normalized LIKE 'BULK-MAT-%'",Integer.class)).isEqualTo(1000);
        });
    }

    @Test void openingInventoryHandlerCreatesImmutableTransactionsBalancesAndDownloadableResult() throws Exception {
        String token=user("opening-owner","PRODUCT_MATERIAL");
        seedMaterial("OPEN-MAT-1","PCS");seedMaterial("OPEN-MAT-2","KG");
        byte[] file=openingRows(new String[][]{
                {"MAIN","OPEN-MAT-1","12.5000","3","2026-10-14","OPEN-REF-1","盘点"},
                {"MAIN","OPEN-MAT-2","0","2","2026-10-14","OPEN-REF-2","零期初"}});
        String batch=mapper.readTree(upload(token,"opening-1","AUXILIARY_MATERIAL_OPENING_INVENTORY",file,202))
                .at("/batch/id").asText();
        commit(token,batch,"opening-commit-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        commit(token,batch,"opening-commit-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/import-batches/{id}/result",batch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdRows").value(2))
                .andExpect(jsonPath("$.writtenRows").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE transaction_type='OPENING'",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_balance b JOIN auxiliary_material m ON m.id=b.auxiliary_material_id WHERE m.material_code_normalized='OPEN-MAT-1'",java.math.BigDecimal.class))
                .isEqualByComparingTo("12.5000");
        assertThat(jdbc.queryForObject("SELECT safety_stock FROM inventory_balance b JOIN auxiliary_material m ON m.id=b.auxiliary_material_id WHERE m.material_code_normalized='OPEN-MAT-2'",java.math.BigDecimal.class))
                .isEqualByComparingTo("2");
        byte[] csv=mvc.perform(get("/api/v1/import-batches/{id}/result-file",batch)
                        .header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String text=new String(csv,java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).startsWith("\uFEFFrecord_type").contains("SUMMARY").contains("OPEN-REF-1").contains("SUCCEEDED");
        String denied=user("opening-reader","SELECTION");
        mvc.perform(get("/api/v1/import-batches/{id}/result-file",batch).header("Authorization",bearer(denied)))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='INVENTORY_OPENING'",Integer.class)).isEqualTo(2);

        String duplicate=mapper.readTree(upload(token,"opening-duplicate","AUXILIARY_MATERIAL_OPENING_INVENTORY",file,202))
                .at("/batch/id").asText();
        mvc.perform(get("/api/v1/import-batches/{id}",duplicate).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commitAllowed").value(true));
        commit(token,duplicate,"opening-duplicate-commit").andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/import-batches/{id}/result",duplicate).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchangedRows").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE transaction_type='OPENING'",Integer.class)).isEqualTo(2);

        String conflict=mapper.readTree(upload(token,"opening-conflict","AUXILIARY_MATERIAL_OPENING_INVENTORY",
                openingRows(new String[][]{{"MAIN","OPEN-MAT-1","12.5000","4","2026-10-14","OPEN-REF-1","盘点"}}),202))
                .at("/batch/id").asText();
        mvc.perform(get("/api/v1/import-batches/{id}",conflict).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.commitAllowed").value(false));
        mvc.perform(get("/api/v1/import-batches/{id}/issues",conflict).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("OPENING_INVENTORY_CONFLICT"));
    }

    @Test void openingInventoryValidatesWarehouseReferencesNumbersDatesAndDuplicates() throws Exception {
        String token=user("opening-owner","PRODUCT_MATERIAL");seedMaterial("VALID-MAT","PCS");
        String batch=mapper.readTree(upload(token,"opening-invalid","AUXILIARY_MATERIAL_OPENING_INVENTORY",
                openingRows(new String[][]{
                        {"OTHER","VALID-MAT","-1","0","2099-01-01","DUP-REF","bad"},
                        {"MAIN","UNKNOWN-MAT","1","-2","2026-10-14","DUP-REF","bad"},
                        {"MAIN","VALID-MAT","1","0","2026-10-14","DUP-REF","bad"},
                        {"MAIN","valid-mat","2","0","2026-10-14","OTHER-REF","bad"}}),202))
                .at("/batch/id").asText();
        String issues=mvc.perform(get("/api/v1/import-batches/{id}/issues",batch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(issues).findValuesAsText("code")).contains("WAREHOUSE_REFERENCE_INVALID",
                "MATERIAL_REFERENCE_INVALID","DECIMAL_INVALID","OPENING_DATE_INVALID",
                "OPENING_MATERIAL_DUPLICATED_IN_FILE");
    }

    @Test void lateOpeningReferenceChangeRollsBackEveryRowAndProducesFailureResult() throws Exception {
        String token=user("opening-owner","PRODUCT_MATERIAL");seedMaterial("LATE-OPEN-1","PCS");seedMaterial("LATE-OPEN-2","PCS");
        String batch=mapper.readTree(upload(token,"opening-late","AUXILIARY_MATERIAL_OPENING_INVENTORY",
                openingRows(new String[][]{
                        {"MAIN","LATE-OPEN-1","4","1","2026-10-14","LATE-REF-1","late"},
                        {"MAIN","LATE-OPEN-2","5","1","2026-10-14","LATE-REF-2","late"}}),202))
                .at("/batch/id").asText();
        jdbc.update("UPDATE auxiliary_material SET status='DISABLED' WHERE material_code_normalized='LATE-OPEN-2'");
        commit(token,batch,"opening-late-commit").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_DATA_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balance",Integer.class)).isZero();
        String csv=mvc.perform(get("/api/v1/import-batches/{id}/result-file",batch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("FAILED").contains("IMPORT_DATA_CHANGED");
    }

    @Test void acceptsAndCommitsOneThousandOpeningRowsWithinGate() {
        assertTimeout(Duration.ofSeconds(30), () -> {
            String token=user("bulk-opening-owner","PRODUCT_MATERIAL");String[][] rows=new String[1000][];
            for(int index=0;index<rows.length;index++){
                String code="BULK-OPEN-"+(index+1);seedMaterial(code,"PCS");
                rows[index]=new String[]{"MAIN",code,"0","0","2026-10-14","BULK-REF-"+(index+1),"bulk"};
            }
            String batch=mapper.readTree(upload(token,"bulk-opening","AUXILIARY_MATERIAL_OPENING_INVENTORY",openingRows(rows),202))
                    .at("/batch/id").asText();
            commit(token,batch,"bulk-opening-commit").andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE transaction_type='OPENING'",Integer.class)).isEqualTo(1000);
        });
    }

    @Test void cancelAndExpiredRevalidationFollowStateMachine() throws Exception {
        String token=user("import-owner","PRODUCT_MATERIAL"); String body=upload(token,"upload-2","AUXILIARY_MATERIALS",
                templates.bytes(ImportTypes.ImportType.AUXILIARY_MATERIALS),202);
        String id=mapper.readTree(body).at("/batch/id").asText();
        mvc.perform(post("/api/v1/import-batches/{id}/cancel",id).header("Authorization",bearer(token))
                        .header("Idempotency-Key","cancel-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/v1/import-batches/{id}/result-file",id).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("CANCELLED"));
        mvc.perform(post("/api/v1/import-batches/{id}/cancel",id).header("Authorization",bearer(token))
                        .header("Idempotency-Key","cancel-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        String source=mapper.readTree(upload(token,"upload-3","PRODUCTS",templates.bytes(ImportTypes.ImportType.PRODUCTS),202)).at("/batch/id").asText();
        jdbc.update("UPDATE import_batch SET preview_expires_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) WHERE id=?",source);
        String successor=mvc.perform(post("/api/v1/import-batches/{id}/revalidate",source)
                        .header("Authorization",bearer(token)).header("Idempotency-Key","revalidate-1"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.batch.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        String successorId=mapper.readTree(successor).at("/batch/id").asText();
        assertThat(jdbc.queryForObject("SELECT status FROM import_batch WHERE id=?",String.class,source)).isEqualTo("VALIDATED");
        assertThat(jdbc.queryForObject("SELECT status FROM import_batch WHERE id=?",String.class,successorId)).isEqualTo("VALIDATED");

        String terminal=mapper.readTree(upload(token,"upload-terminal","PRODUCTS",templates.bytes(ImportTypes.ImportType.PRODUCTS),202)).at("/batch/id").asText();
        jdbc.update("UPDATE import_batch SET status='SUCCEEDED',phase='DONE' WHERE id=?",terminal);
        mvc.perform(post("/api/v1/import-batches/{id}/cancel",terminal).header("Authorization",bearer(token))
                        .header("Idempotency-Key","cancel-terminal"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IMPORT_NOT_CANCELLABLE"));
    }

    @Test void rejectsRetiredCatalogBatchButKeepsTerminalResultDownloadable() throws Exception {
        String token=user("import-version-owner","PRODUCT_MATERIAL");
        var retiredUpload=new MockMultipartFile("file","products-v1.xlsx",ImportService.XLSX_MIME,
                templates.bytes(ImportTypes.ImportType.PRODUCTS));
        mvc.perform(multipart("/api/v1/import-batches").file(retiredUpload).param("importType","PRODUCTS")
                        .param("templateVersion","1.0").header("Authorization",bearer(token))
                        .header("Idempotency-Key","retired-upload"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMPORT_TEMPLATE_VERSION_MISMATCH"));

        String pending=mapper.readTree(upload(token,"version-pending","PRODUCTS",
                productRows("VERSION-PENDING","待升级批次"),202)).at("/batch/id").asText();
        jdbc.update("UPDATE import_batch SET template_version='1.0' WHERE id=?",pending);
        commit(token,pending,"version-pending-commit").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_TEMPLATE_VERSION_RETIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM import_batch WHERE id=?",String.class,pending)).isEqualTo("VALIDATED");
        jdbc.update("UPDATE import_batch SET preview_expires_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP) WHERE id=?",pending);
        mvc.perform(post("/api/v1/import-batches/{id}/revalidate",pending)
                        .header("Authorization",bearer(token)).header("Idempotency-Key","retired-revalidate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_TEMPLATE_VERSION_RETIRED"));

        String completed=mapper.readTree(upload(token,"version-completed","PRODUCTS",
                productRows("VERSION-COMPLETED","已完成历史批次"),202)).at("/batch/id").asText();
        commit(token,completed,"version-completed-commit").andExpect(status().isAccepted());
        jdbc.update("UPDATE import_batch SET template_version='1.0' WHERE id=?",completed);
        mvc.perform(get("/api/v1/import-batches/{id}/result",completed).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.batch.templateVersion").value("1.0"));
        mvc.perform(get("/api/v1/import-batches/{id}/result-file",completed).header("Authorization",bearer(token)))
                .andExpect(status().isOk());
    }

    @Test void formulaProducesBlockingRowIssueAndNeverWritesBusinessData() throws Exception {
        int productCount=jdbc.queryForObject("SELECT COUNT(*) FROM product",Integer.class);
        String token=user("import-owner","PRODUCT_MATERIAL"); String body=upload(token,"formula","PRODUCTS",formulaRow(),202);
        String id=mapper.readTree(body).at("/batch/id").asText();
        mvc.perform(get("/api/v1/import-batches/{id}",id).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.errorCount").value(1)).andExpect(jsonPath("$.commitAllowed").value(false));
        mvc.perform(get("/api/v1/import-batches/{id}/issues",id).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("IMPORT_FORMULA_NOT_ALLOWED"))
                .andExpect(jsonPath("$[0].row").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product",Integer.class)).isEqualTo(productCount);
    }

    @Test void rejectsHistoricalTypeUnsafeZipWrongHeaderAndUnauthorizedRole() throws Exception {
        String token=user("import-owner","PRODUCT_MATERIAL");
        upload(token,"listing","STORE_LISTINGS",templates.bytes(ImportTypes.ImportType.PRODUCTS),400);
        upload(token,"unsafe","PRODUCTS",unsafeZip(),422);
        byte[] wrongHeader=wrongHeader(); upload(token,"header","PRODUCTS",wrongHeader,422);
        String selector=user("selector","SELECTION"); upload(selector,"denied","PRODUCTS",
                templates.bytes(ImportTypes.ImportType.PRODUCTS),403);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM import_batch",Integer.class)).isZero();
    }

    private byte[] wrongHeader() throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.PRODUCTS)));
            var output=new ByteArrayOutputStream()){
            var row=workbook.getSheet("导入模板").getRow(0);
            row.removeCell(row.getCell(0));
            row.createCell(0).setCellValue("wrong_header");
            workbook.write(output);
            byte[] changed=output.toByteArray();
            try(var roundTrip=new XSSFWorkbook(new ByteArrayInputStream(changed))){
                assertThat(roundTrip.getSheet("导入模板").getRow(0).getCell(0).getStringCellValue()).isEqualTo("wrong_header");
            }
            return changed;
        }
    }
    private List<String> headers(XSSFWorkbook workbook,String sheet) {
        var row=workbook.getSheet(sheet).getRow(0);var values=new java.util.ArrayList<String>();
        for(int index=0;index<row.getLastCellNum();index++)values.add(row.getCell(index).getStringCellValue());
        return values;
    }
    private String meta(XSSFWorkbook workbook,String key) {
        for(var row:workbook.getSheet("_meta"))if(key.equals(row.getCell(0).getStringCellValue()))return row.getCell(1).getStringCellValue();
        throw new AssertionError("missing metadata "+key);
    }
    private byte[] unsafeZip() throws Exception {try(var output=new ByteArrayOutputStream();var zip=new ZipOutputStream(output)){
        zip.putNextEntry(new ZipEntry("../evil"));zip.write(new byte[]{1});zip.closeEntry();zip.putNextEntry(new ZipEntry("[Content_Types].xml"));zip.write(new byte[]{1});zip.closeEntry();zip.finish();return output.toByteArray();}}
    private byte[] formulaRow() throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.PRODUCTS)));
            var output=new ByteArrayOutputStream()){
            var row=workbook.getSheet("导入模板").createRow(1); row.createCell(0).setCellFormula("1+1"); row.createCell(1).setCellValue("测试产品");
            workbook.write(output); return output.toByteArray();
        }
    }
    private byte[] productRows(String code,String name) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.PRODUCTS)));
            var output=new ByteArrayOutputStream()){
            var row=workbook.getSheet("导入模板").createRow(1);row.createCell(0).setCellValue(code);row.createCell(1).setCellValue(name);row.createCell(4).setCellValue("ACTIVE");
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] variantRows(String sku,String product,String color) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.VARIANTS)));
            var output=new ByteArrayOutputStream()){
            var variant=workbook.getSheet("variants_导入模板").createRow(1);variant.createCell(0).setCellValue(sku);variant.createCell(1).setCellValue(product);variant.createCell(2).setCellValue("导入规格 "+color);variant.createCell(11).setCellValue(500);variant.createCell(12).setCellValue("ACTIVE");
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] variantRowsWithoutAttributes(String product) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.VARIANTS)));
            var output=new ByteArrayOutputStream()){
            var sheet=workbook.getSheet("variants_导入模板");
            var first=sheet.createRow(1);first.createCell(0).setCellValue("ATTRIBUTE-FREE-SKU-1");first.createCell(1).setCellValue(product);first.createCell(2).setCellValue("无属性 SKU 1");first.createCell(12).setCellValue("ACTIVE");
            var second=sheet.createRow(2);second.createCell(0).setCellValue("ATTRIBUTE-FREE-SKU-2");second.createCell(1).setCellValue(product);second.createCell(2).setCellValue("无属性 SKU 2");second.createCell(12).setCellValue("DRAFT");
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] structuredVariantRows(String sku,String product) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.VARIANTS)));
            var output=new ByteArrayOutputStream()){
            var row=workbook.getSheet("variants_导入模板").createRow(1);
            row.createCell(0).setCellValue(sku);row.createCell(1).setCellValue(product);row.createCell(2).setCellValue("结构化规格");
            row.createCell(5).setCellValue(10);row.createCell(6).setCellValue(20);row.createCell(7).setCellValue(5);
            row.createCell(8).setCellValue(12);row.createCell(9).setCellValue(22);row.createCell(10).setCellValue(7);
            row.createCell(11).setCellValue(500);row.createCell(12).setCellValue("ACTIVE");
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] legacyVariantRows(String sku,String product) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.VARIANTS,"2.0")));
            var output=new ByteArrayOutputStream()){
            var row=workbook.getSheet("variants_导入模板").createRow(1);
            row.createCell(0).setCellValue(sku);row.createCell(1).setCellValue(product);row.createCell(2).setCellValue("旧模板更新");
            row.createCell(5).setCellValue(600);row.createCell(6).setCellValue("ACTIVE");
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] productRows(int count) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.PRODUCTS)));
            var output=new ByteArrayOutputStream()){
            var sheet=workbook.getSheet("导入模板");
            for(int index=1;index<=count;index++){var row=sheet.createRow(index);row.createCell(0).setCellValue("BULK-P-"+index);row.createCell(1).setCellValue("批量产品 "+index);row.createCell(4).setCellValue("ACTIVE");}
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] materialRows(String[][] values) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.AUXILIARY_MATERIALS)));
            var output=new ByteArrayOutputStream()){
            var sheet=workbook.getSheet("导入模板");
            for(int index=0;index<values.length;index++){
                var value=values[index];var row=sheet.createRow(index+1);
                row.createCell(0).setCellValue(value[0]);row.createCell(1).setCellValue(value[1]);
                row.createCell(2).setCellValue(value[2]);row.createCell(3).setCellValue("标准规格");
                row.createCell(4).setCellValue(value[3]);row.createCell(6).setCellValue(value[4]);
                row.createCell(7).setCellValue("ERP-016 test");
            }
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] relationRows(String[][] values) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.PRODUCT_AUXILIARY_MATERIALS)));
            var output=new ByteArrayOutputStream()){
            var sheet=workbook.getSheet("导入模板");
            for(int index=0;index<values.length;index++){
                var value=values[index];var row=sheet.createRow(index+1);
                row.createCell(0).setCellValue(value[0]);row.createCell(1).setCellValue(value[1]);
                row.createCell(2).setCellValue(value[2]);row.createCell(3).setCellValue(value[3]);
                row.createCell(4).setCellValue(value[4]);row.createCell(5).setCellValue(value[5]);
                row.createCell(6).setCellValue("ERP-016 test");
            }
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] openingRows(String[][] values) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.AUXILIARY_MATERIAL_OPENING_INVENTORY)));
            var output=new ByteArrayOutputStream()){
            var sheet=workbook.getSheet("导入模板");
            for(int index=0;index<values.length;index++){
                var value=values[index];var row=sheet.createRow(index+1);
                for(int column=0;column<value.length;column++)row.createCell(column).setCellValue(value[column]);
            }
            workbook.write(output);return output.toByteArray();
        }
    }
    private void seedProductAndVariants(String productCode,String... skus) {
        String productId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product (id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES (?,?,?,'导入依赖产品','ACTIVE','system','system')",
                productId,productCode,productCode.toUpperCase(java.util.Locale.ROOT));
        for(String sku:skus) jdbc.update("INSERT INTO product_variant (id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES (?,?,?,?,?,'ACTIVE',?)",
                UUID.randomUUID().toString(),productId,sku,sku.toUpperCase(java.util.Locale.ROOT),sku,"signature-"+sku);
    }
    private void seedMaterial(String code,String unit) {
        jdbc.update("INSERT INTO auxiliary_material (id,material_code,material_code_normalized,name,material_type,unit,status,created_by,updated_by) VALUES (?,?,?,?,? ,?,'ACTIVE','system','system')",
                UUID.randomUUID().toString(),code,code.toUpperCase(java.util.Locale.ROOT),code,"BOX",unit);
    }
    private org.springframework.test.web.servlet.ResultActions commit(String token,String id,String key)throws Exception{
        String batch=mvc.perform(get("/api/v1/import-batches/{id}",id).header("Authorization",bearer(token))).andReturn().getResponse().getContentAsString();
        var value=mapper.readTree(batch);
        return mvc.perform(post("/api/v1/import-batches/{id}/commit",id).header("Authorization",bearer(token)).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("previewVersion",value.get("previewVersion").asText(),"fileHash",value.get("fileHash").asText(),"warningsAccepted",true))));
    }
    private String upload(String token,String key,String type,byte[] bytes,int expectedStatus) throws Exception {
        String version="VARIANTS".equals(type)?"2.1":"PRODUCTS".equals(type)?"2.0":"1.0";
        return uploadVersion(token,key,type,version,bytes,expectedStatus);
    }
    private String uploadVersion(String token,String key,String type,String version,byte[] bytes,int expectedStatus) throws Exception {
        var file=new MockMultipartFile("file","upload.xlsx",ImportService.XLSX_MIME,bytes);
        return mvc.perform(multipart("/api/v1/import-batches").file(file).param("importType",type).param("templateVersion",version)
                        .header("Authorization",bearer(token)).header("Idempotency-Key",key))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }
    private String user(String login,String role)throws Exception{String id=UUID.randomUUID().toString();var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        jdbc.update("INSERT INTO iam_user (id,login_name,display_name,password_hash,role,status,must_change_password) VALUES (?,?,?,?,?,'ENABLED',FALSE)",id,login,login,encoder.encode(PASSWORD),role);
        String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("loginName",login,"password",PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return mapper.readTree(body).get("token").asText();}
    private String bearer(String token){return "Bearer "+token;}
}
