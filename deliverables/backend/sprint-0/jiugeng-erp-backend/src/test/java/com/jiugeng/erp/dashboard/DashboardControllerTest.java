package com.jiugeng.erp.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {
    private static final String PASSWORD="Dashboard-42";
    private static Path imageDirectory;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DashboardService dashboardService;

    @BeforeAll static void directory() throws Exception { imageDirectory=Files.createTempDirectory("dashboard-images-"); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("app.dashboard.image-dir",()->imageDirectory.toString());}
    @AfterAll static void removeDirectory() throws Exception {if(imageDirectory!=null)try(var paths=Files.walk(imageDirectory)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(Exception ignored){}});}}

    @BeforeEach void reset(){clean();}
    @AfterEach void after(){clean();}

    @Test
    void enforcesRoleBoundariesIdempotencyStateTransitionsAndTenItemHomeLimit() throws Exception {
        String productId=createProduct("DASH-001","工作台产品");
        String productToken=user("product-user","PRODUCT_MATERIAL");
        String selectionToken=user("selection-user","SELECTION");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        Map<String,Object> pending=shipment(today.plusDays(1),productId,5,2,LocalDateTime.of(today.plusDays(1),java.time.LocalTime.of(18,0)));

        String first=mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments")
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","pending-create-1")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(pending)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantityUnit").value("SET"))
                .andReturn().getResponse().getContentAsString();
        String pendingId=json.readTree(first).get("id").asText();
        mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments")
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","pending-create-1")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(pending)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(pendingId));
        Map<String,Object> changed=new LinkedHashMap<>(pending);changed.put("quantity",6);
        mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments")
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","pending-create-1")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        mvc.perform(get("/api/v1/dashboard/pending-shipments").header("Authorization",bearer(selectionToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get("/api/v1/dashboard-maintenance/pending-shipments").header("Authorization",bearer(selectionToken)))
                .andExpect(status().isForbidden());

        for(int i=0;i<11;i++){
            Map<String,Object> input=Map.of("productName","新品"+i,"expectedQuantity",i+1,"remark","预计",
                    "expectedCompletionDate",today.plusDays(i).toString());
            mvc.perform(post("/api/v1/dashboard-maintenance/pending-products")
                            .header("Authorization",bearer(selectionToken)).header("Idempotency-Key","new-product-"+i)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/dashboard/pending-products").header("Authorization",bearer(productToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.totalCount").value(11)).andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.items[0].productName").value("新品0"));

        Map<String,Object> transfer=Map.of("quantity",5,"boxCount",2,"remark","当日安排",
                "latestShipTime",LocalDateTime.of(today,java.time.LocalTime.of(18,0)).toString(),"version",0);
        String todayBody=mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments/{id}/transfer-to-today",pendingId)
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","transfer-1")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(transfer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.shipDate").value(today.toString()))
                .andExpect(jsonPath("$.sourcePendingShipmentId").value(pendingId))
                .andReturn().getResponse().getContentAsString();
        String todayId=json.readTree(todayBody).get("id").asText();
        mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments/{id}/transfer-to-today",pendingId)
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","transfer-1")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(transfer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(todayId));
        assertThat(jdbc.queryForObject("SELECT status FROM dashboard_pending_shipment WHERE id=?",String.class,pendingId))
                .isEqualTo("TRANSFERRED_TO_TODAY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dashboard_today_shipment WHERE source_pending_shipment_id=?",Integer.class,pendingId)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT action FROM audit_event WHERE resource_type LIKE '%SHIPMENT%'",String.class))
                .contains("DASHBOARD_PENDING_SHIPMENT_CREATE","DASHBOARD_PENDING_SHIPMENT_TRANSFER");
    }

    @Test
    void validatesOptimisticLockAndSecureImageLifecycle() throws Exception {
        String selectionToken=user("selector","SELECTION");
        byte[] png=png();
        MockMultipartFile file=new MockMultipartFile("file","pattern.png","image/png",png);
        String body=mvc.perform(multipart("/api/v1/dashboard-images").file(file).param("purpose","PENDING_PRODUCT")
                        .header("Authorization",bearer(selectionToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mediaType").value("image/png"))
                .andReturn().getResponse().getContentAsString();
        String imageId=json.readTree(body).get("id").asText();
        mvc.perform(multipart("/api/v1/dashboard-images").file(file).param("purpose","SHIPMENT")
                        .header("Authorization",bearer(selectionToken)))
                .andExpect(status().isForbidden());
        MockMultipartFile bad=new MockMultipartFile("file","fake.png","image/png","not an image".getBytes());
        mvc.perform(multipart("/api/v1/dashboard-images").file(bad).param("purpose","PENDING_PRODUCT")
                        .header("Authorization",bearer(selectionToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DASHBOARD_IMAGE_INVALID"));

        LocalDate date=LocalDate.now().plusDays(4);
        Map<String,Object> input=new LinkedHashMap<>();input.put("productName","带图新品");input.put("imageId",imageId);
        input.put("expectedQuantity",10);input.put("remark","图案");input.put("expectedCompletionDate",date.toString());
        String created=mvc.perform(post("/api/v1/dashboard-maintenance/pending-products")
                        .header("Authorization",bearer(selectionToken)).header("Idempotency-Key","image-product")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode node=json.readTree(created);String id=node.get("id").asText();
        assertThat(jdbc.queryForObject("SELECT status FROM dashboard_image_asset WHERE id=?",String.class,imageId)).isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/dashboard-images/{id}/thumbnail",imageId).header("Authorization",bearer(selectionToken)))
                .andExpect(status().isOk()).andExpect(result->assertThat(result.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff"));

        Map<String,Object> changed=new LinkedHashMap<>(input);changed.put("productName","更新名称");
        Map<String,Object> update=Map.of("product",changed,"version",0);
        mvc.perform(patch("/api/v1/dashboard-maintenance/pending-products/{id}",id)
                        .header("Authorization",bearer(selectionToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/api/v1/dashboard-maintenance/pending-products/{id}",id)
                        .header("Authorization",bearer(selectionToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(update)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DASHBOARD_VERSION_CONFLICT"));
    }

    @Test
    void removesExpiredUnattachedImagesFromDatabaseAndDisk() throws Exception {
        String selectionToken=user("orphan-owner","SELECTION");
        MockMultipartFile file=new MockMultipartFile("file","orphan.png","image/png",png());
        String body=mvc.perform(multipart("/api/v1/dashboard-images").file(file).param("purpose","PENDING_PRODUCT")
                        .header("Authorization",bearer(selectionToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String imageId=json.readTree(body).get("id").asText();
        Map<String,Object> keys=jdbc.queryForMap("SELECT original_storage_key,thumbnail_storage_key FROM dashboard_image_asset WHERE id=?",imageId);
        Path original=imageDirectory.resolve(keys.get("original_storage_key").toString());
        Path thumbnail=imageDirectory.resolve(keys.get("thumbnail_storage_key").toString());
        assertThat(original).exists();assertThat(thumbnail).exists();
        jdbc.update("UPDATE dashboard_image_asset SET created_at=DATEADD('DAY',-2,CURRENT_TIMESTAMP) WHERE id=?",imageId);

        dashboardService.cleanupOrphans();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dashboard_image_asset WHERE id=?",Integer.class,imageId)).isZero();
        assertThat(original).doesNotExist();assertThat(thumbnail).doesNotExist();
    }

    private byte[] png() throws Exception {BufferedImage image=new BufferedImage(32,24,BufferedImage.TYPE_INT_RGB);ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();}
    private Map<String,Object> shipment(LocalDate date,String productId,int quantity,int boxes,LocalDateTime latest){return Map.of(
            "date",date.toString(),"productId",productId,"quantity",quantity,"boxCount",boxes,"remark","安排",
            "latestShipTime",latest.toString());}
    private String createProduct(String code,String name){String id=UUID.randomUUID().toString();jdbc.update("""
            INSERT INTO product (id,product_code,product_code_normalized,name_zh,status,created_by,updated_by)
            VALUES (?,?,?,?,'ACTIVE','test','test')
            """,id,code,code.toUpperCase(),name);return id;}
    private String user(String login,String role)throws Exception{String id=UUID.randomUUID().toString();var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();jdbc.update("""
            INSERT INTO iam_user (id,login_name,display_name,password_hash,role,status,must_change_password)
            VALUES (?,?,?,?,?,'ENABLED',FALSE)
            """,id,login,login,encoder.encode(PASSWORD),role);String response=mvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",login,"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(response).get("token").asText();}
    private String bearer(String token){return "Bearer "+token;}
    private void clean(){for(String table:new String[]{"dashboard_command","dashboard_today_shipment","dashboard_pending_shipment",
            "dashboard_pending_product","dashboard_image_asset","product_auxiliary_material","variant_attribute","product_variant","product",
            "iam_password_reset_challenge","audit_event","iam_session","iam_user"}){jdbc.update("DELETE FROM "+table);}}
}
