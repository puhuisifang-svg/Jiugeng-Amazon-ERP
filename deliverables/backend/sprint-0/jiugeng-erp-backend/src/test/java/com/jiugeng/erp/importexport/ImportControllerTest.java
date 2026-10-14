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
        jdbc.update("DELETE FROM product_auxiliary_material"); jdbc.update("DELETE FROM variant_attribute");
        jdbc.update("DELETE FROM product_variant"); jdbc.update("DELETE FROM product");
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

    @Test void productAndVariantHandlersUpsertAtomicallyAndReportCounts() throws Exception {
        String token=user("import-owner","PRODUCT_MATERIAL");
        String productBody=upload(token,"products-1","PRODUCTS",productRows("IMP-P001","导入产品"),202);
        String productId=mapper.readTree(productBody).at("/batch/id").asText();
        commit(token,productId,"products-commit-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/import-batches/{id}/result",productId).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdRows").value(1))
                .andExpect(jsonPath("$.writtenRows").value(1));
        assertThat(jdbc.queryForObject("SELECT name_zh FROM product WHERE product_code_normalized='IMP-P001'",String.class)).isEqualTo("导入产品");

        String variantBody=upload(token,"variants-1","VARIANTS",variantRows("IMP-SKU001","IMP-P001","Black"),202);
        String variantBatch=mapper.readTree(variantBody).at("/batch/id").asText();
        commit(token,variantBatch,"variants-commit-1").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE sku_code_normalized='IMP-SKU001'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT attribute_value FROM variant_attribute WHERE attribute_code_normalized='COLOR'",String.class)).isEqualTo("Black");

        String replayBody=upload(token,"variants-2","VARIANTS",variantRows("IMP-SKU001","IMP-P001","Black"),202);
        String replayBatch=mapper.readTree(replayBody).at("/batch/id").asText();
        commit(token,replayBatch,"variants-commit-2").andExpect(status().isAccepted());
        mvc.perform(get("/api/v1/import-batches/{id}/result",replayBatch).header("Authorization",bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unchangedRows").value(1));
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

    @Test void acceptsAndCommitsOneThousandProductRowsWithinGate() {
        assertTimeout(Duration.ofSeconds(30), () -> {
            String token=user("bulk-owner","PRODUCT_MATERIAL");
            String batch=mapper.readTree(upload(token,"bulk-products","PRODUCTS",productRows(1000),202)).at("/batch/id").asText();
            commit(token,batch,"bulk-products-commit").andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE product_code_normalized LIKE 'BULK-P-%'",Integer.class)).isEqualTo(1000);
        });
    }

    @Test void cancelAndExpiredRevalidationFollowStateMachine() throws Exception {
        String token=user("import-owner","PRODUCT_MATERIAL"); String body=upload(token,"upload-2","AUXILIARY_MATERIALS",
                templates.bytes(ImportTypes.ImportType.AUXILIARY_MATERIALS),202);
        String id=mapper.readTree(body).at("/batch/id").asText();
        mvc.perform(post("/api/v1/import-batches/{id}/cancel",id).header("Authorization",bearer(token))
                        .header("Idempotency-Key","cancel-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
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
            var row=workbook.getSheet("导入模板").createRow(1);row.createCell(0).setCellValue(code);row.createCell(1).setCellValue(name);row.createCell(7).setCellValue("ACTIVE");
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] variantRows(String sku,String product,String color) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.VARIANTS)));
            var output=new ByteArrayOutputStream()){
            var variant=workbook.getSheet("variants_导入模板").createRow(1);variant.createCell(0).setCellValue(sku);variant.createCell(1).setCellValue(product);variant.createCell(2).setCellValue("导入规格");variant.createCell(8).setCellValue(0.5);variant.createCell(9).setCellValue("KG");variant.createCell(10).setCellValue("ACTIVE");
            var attribute=workbook.getSheet("attributes_导入模板").createRow(1);attribute.createCell(0).setCellValue(sku);attribute.createCell(1).setCellValue("color");attribute.createCell(2).setCellValue(color);attribute.createCell(3).setCellValue(1);
            workbook.write(output);return output.toByteArray();
        }
    }
    private byte[] productRows(int count) throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(templates.bytes(ImportTypes.ImportType.PRODUCTS)));
            var output=new ByteArrayOutputStream()){
            var sheet=workbook.getSheet("导入模板");
            for(int index=1;index<=count;index++){var row=sheet.createRow(index);row.createCell(0).setCellValue("BULK-P-"+index);row.createCell(1).setCellValue("批量产品 "+index);row.createCell(7).setCellValue("ACTIVE");}
            workbook.write(output);return output.toByteArray();
        }
    }
    private org.springframework.test.web.servlet.ResultActions commit(String token,String id,String key)throws Exception{
        String batch=mvc.perform(get("/api/v1/import-batches/{id}",id).header("Authorization",bearer(token))).andReturn().getResponse().getContentAsString();
        var value=mapper.readTree(batch);
        return mvc.perform(post("/api/v1/import-batches/{id}/commit",id).header("Authorization",bearer(token)).header("Idempotency-Key",key)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("previewVersion",value.get("previewVersion").asText(),"fileHash",value.get("fileHash").asText(),"warningsAccepted",true))));
    }
    private String upload(String token,String key,String type,byte[] bytes,int expectedStatus) throws Exception {
        var file=new MockMultipartFile("file","upload.xlsx",ImportService.XLSX_MIME,bytes);
        return mvc.perform(multipart("/api/v1/import-batches").file(file).param("importType",type).param("templateVersion","1.0")
                        .header("Authorization",bearer(token)).header("Idempotency-Key",key))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }
    private String user(String login,String role)throws Exception{String id=UUID.randomUUID().toString();var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        jdbc.update("INSERT INTO iam_user (id,login_name,display_name,password_hash,role,status,must_change_password) VALUES (?,?,?,?,?,'ENABLED',FALSE)",id,login,login,encoder.encode(PASSWORD),role);
        String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("loginName",login,"password",PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return mapper.readTree(body).get("token").asText();}
    private String bearer(String token){return "Bearer "+token;}
}
