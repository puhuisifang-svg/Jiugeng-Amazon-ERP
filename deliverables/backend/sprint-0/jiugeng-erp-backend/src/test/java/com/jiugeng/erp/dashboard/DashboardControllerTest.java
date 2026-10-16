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
    @Test
    void shipmentPreflightAllowsIdempotencyHeader() throws Exception {
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .options("/api/v1/dashboard-maintenance/today-shipments")
                .header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Access-Control-Allow-Headers")).containsIgnoringCase("idempotency-key");
    }
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
    void dispatchIsAuthorizedIdempotentImmutableAndIncludedInStatistics() throws Exception {
        String product=createProduct("SHIP-001","发货统计产品");
        String manager=user("ship-manager","PRODUCT_MATERIAL"), reader=user("ship-reader","SELECTION");
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var input=shipment(date,product,5,2,date.atTime(23,59));
        String created=mvc.perform(post("/api/v1/dashboard-maintenance/today-shipments")
            .header("Authorization",bearer(manager)).header("Idempotency-Key","create-dispatch")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(created).get("id").asText();
        String endpoint="/api/v1/shipping/today-shipments/"+id+"/dispatch";
        mvc.perform(post(endpoint).header("Authorization",bearer(reader)).header("Idempotency-Key","dispatch-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post(endpoint).header("Authorization",bearer(manager)).header("Idempotency-Key","stale-dispatch")
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":99}")).andExpect(status().isConflict());
        String dispatched=mvc.perform(post(endpoint).header("Authorization",bearer(manager)).header("Idempotency-Key","dispatch-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(5)).andExpect(jsonPath("$.businessDate").value(date.toString()))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(post(endpoint).header("Authorization",bearer(manager)).header("Idempotency-Key","dispatch-1")
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(json.readTree(dispatched).get("id").asText()));
        mvc.perform(post(endpoint).header("Authorization",bearer(manager)).header("Idempotency-Key","dispatch-again")
            .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/dashboard/today-shipments").header("Authorization",bearer(manager)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(patch("/api/v1/dashboard-maintenance/today-shipments/"+id).header("Authorization",bearer(manager))
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("shipment",input,"version",1))))
            .andExpect(status().isConflict());
        for(String action:new String[]{"archive","transfer-to-pending"}) {
            mvc.perform(post("/api/v1/dashboard-maintenance/today-shipments/"+id+"/"+action)
                .header("Authorization",bearer(manager)).header("Idempotency-Key",action+"-shipped")
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"reason\":\"误操作\"}"))
                .andExpect(status().isConflict());
        }
        jdbc.update("UPDATE product SET name_zh='后续改名' WHERE id=?",product);
        mvc.perform(get("/api/v1/shipping/statistics").param("from",date.toString()).param("to",date.toString())
            .header("Authorization",bearer(reader))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/shipping/statistics").param("from",date.toString()).param("to",date.toString()).param("keyword","SHIP-001")
            .header("Authorization",bearer(manager))).andExpect(status().isOk())
            .andExpect(jsonPath("$.summary.records").value(1)).andExpect(jsonPath("$.summary.quantity").value(5))
            .andExpect(jsonPath("$.summary.boxCount").value(2)).andExpect(jsonPath("$.summary.products").value(1))
            .andExpect(jsonPath("$.daily[0].quantity").value(5)).andExpect(jsonPath("$.products[0].records").value(1))
            .andExpect(jsonPath("$.details.items[0].productName").value("发货统计产品"));
        mvc.perform(get("/api/v1/shipping/statistics").param("from",date.toString()).param("to",date.toString()).param("keyword","%")
            .header("Authorization",bearer(manager))).andExpect(status().isOk()).andExpect(jsonPath("$.summary.records").value(0));
        mvc.perform(get("/api/v1/shipping/statistics").param("from",date.plusDays(1).toString()).param("to",date.toString())
            .header("Authorization",bearer(manager))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/shipping/statistics").param("from",date.minusDays(366).toString()).param("to",date.toString())
            .header("Authorization",bearer(manager))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dashboard_shipment_dispatch",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='SHIPMENT_DISPATCH'",Integer.class)).isEqualTo(1);
    }

    @Test
    void shipmentLabelsValidatePermissionsAndRemainAvailableAfterReturn() throws Exception {
        String productId=createProduct("DOC-001","标签测试");
        String manager=user("doc-manager","PRODUCT_MATERIAL"), reader=user("doc-reader","SELECTION");
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String result=mvc.perform(post("/api/v1/dashboard-maintenance/today-shipments").header("Authorization",bearer(manager))
            .header("Idempotency-Key","doc-today").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(shipment(today,productId,3,1,today.atTime(23,59)))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(result).get("id").asText();
        String endpoint="/api/v1/shipment-documents/today-shipments/"+id;
        var file=new MockMultipartFile("file","标签.pdf","application/pdf","%PDF-1.7 synthetic label".getBytes());
        mvc.perform(multipart(endpoint).file(file).param("kind","LABEL").header("Authorization",bearer(reader))).andExpect(status().isForbidden());
        mvc.perform(multipart(endpoint).file(new MockMultipartFile("file","bad.pdf","application/pdf","not-pdf".getBytes()))
            .param("kind","LABEL").header("Authorization",bearer(manager))).andExpect(status().isBadRequest());
        mvc.perform(multipart(endpoint).file(new MockMultipartFile("file","../label.pdf","application/pdf","%PDF-test".getBytes()))
            .param("kind","LABEL").header("Authorization",bearer(manager))).andExpect(status().isBadRequest());
        mvc.perform(multipart(endpoint).file(new MockMultipartFile("file","empty.txt","text/plain",new byte[0]))
            .param("kind","LABEL").header("Authorization",bearer(manager))).andExpect(status().isBadRequest());
        String uploaded=mvc.perform(multipart(endpoint).file(file).param("kind","LABEL").header("Authorization",bearer(manager)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fileName").value("标签.pdf")).andReturn().getResponse().getContentAsString();
        String document=json.readTree(uploaded).get("id").asText();
        mvc.perform(multipart(endpoint).file(file).param("kind","CARTON_LABEL").header("Authorization",bearer(manager))).andExpect(status().isOk());
        mvc.perform(get(endpoint).param("kind","LABEL").header("Authorization",bearer(reader)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(endpoint+"/"+document+"/download")).andExpect(status().isUnauthorized());
        var download=mvc.perform(get(endpoint+"/"+document+"/download").header("Authorization",bearer(reader)))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(download.getContentAsByteArray()).isEqualTo(file.getBytes());
        assertThat(download.getHeader("Content-Disposition")).startsWith("attachment;");
        mvc.perform(get("/api/v1/shipment-documents/today-shipments/missing/"+document+"/download").header("Authorization",bearer(reader))).andExpect(status().isNotFound());
        String returned=mvc.perform(post("/api/v1/dashboard-maintenance/today-shipments/"+id+"/transfer-to-pending")
            .header("Authorization",bearer(manager)).header("Idempotency-Key","doc-return").contentType(MediaType.APPLICATION_JSON)
            .content("{\"version\":0,\"reason\":\"调整安排\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String pending=json.readTree(returned).get("id").asText();
        mvc.perform(get("/api/v1/shipment-documents/pending-shipments/"+pending).param("kind","LABEL").header("Authorization",bearer(reader)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(document));
        mvc.perform(get("/api/v1/shipment-documents/pending-shipments/"+pending+"/"+document+"/download").header("Authorization",bearer(reader))).andExpect(status().isOk());
        mvc.perform(multipart(endpoint).file(file).param("kind","LABEL").header("Authorization",bearer(manager))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='SHIPMENT_DOCUMENT_UPLOAD'",Integer.class)).isEqualTo(2);
    }

    @Test
    void dispatchStatisticsUseInclusiveBusinessDatesAndTotalsIndependentOfPaging() throws Exception {
        String manager=user("stats-manager","PRODUCT_MATERIAL");
        for(int i=0;i<3;i++) {
            jdbc.update("""
                INSERT INTO dashboard_shipment_dispatch(id,today_shipment_id,product_id,product_code,product_name,sku_codes,
                  quantity,box_count,remark,planned_date,business_date,dispatched_at,actor_id,actor_name,request_key,source_version)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ""","stat-"+i,"today-stat-"+i,"product-1","P-001","测试快照","SKU_1",i+1,1,"测试",
                java.sql.Date.valueOf("2026-10-01"),java.sql.Date.valueOf("2026-10-"+(i==2?"03":"02")),
                java.sql.Timestamp.from(java.time.Instant.parse("2026-10-01T16:00:00Z")),"test","操作人","req-"+i,0);
        }
        mvc.perform(get("/api/v1/shipping/statistics").header("Authorization",bearer(manager))
            .param("from","2026-10-02").param("to","2026-10-02").param("keyword","SKU_1").param("size","1").param("page","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.summary.records").value(2))
            .andExpect(jsonPath("$.summary.quantity").value(3)).andExpect(jsonPath("$.summary.boxCount").value(2))
            .andExpect(jsonPath("$.daily.length()").value(1)).andExpect(jsonPath("$.daily[0].date").value("2026-10-02"))
            .andExpect(jsonPath("$.details.items.length()").value(1)).andExpect(jsonPath("$.details.totalPages").value(2));
    }

    @Test
    void enforcesRoleBoundariesIdempotencyStateTransitionsAndTenItemHomeLimit() throws Exception {
        String productId=createProduct("DASH-001","工作台产品");
        String productToken=user("product-user","PRODUCT_MATERIAL");
        String selectionToken=user("selection-user","SELECTION");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        Map<String,Object> pending=shipment(today.plusDays(1),productId,5,2,LocalDateTime.of(today.plusDays(3),java.time.LocalTime.of(23,59)));
        mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments")
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","invalid-deadline")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                                shipment(today.plusDays(1),productId,5,2,today.atTime(23,59)))))
                .andExpect(status().isBadRequest());

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
        mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments/{id}/archive",pendingId)
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","delete-history-stale")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"用户删除\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments/{id}/archive",pendingId)
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","delete-history")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"reason\":\"用户删除\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        assertThat(jdbc.queryForObject("SELECT status FROM dashboard_today_shipment WHERE id=?",String.class,todayId)).isEqualTo("ACTIVE");
        mvc.perform(get("/api/v1/dashboard-maintenance/pending-shipments").header("Authorization",bearer(productToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/dashboard-maintenance/pending-shipments").param("status","ARCHIVED").header("Authorization",bearer(productToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void transfersTodayBackToPendingAtomicallyWithPermissionsAndIdempotency() throws Exception {
        String token=user("shipment-editor","PRODUCT_MATERIAL");
        String reader=user("shipment-reader","SELECTION");
        String productId=createProduct("RETURN-001","转待发货测试");
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var input=shipment(today,productId,7,3,today.atTime(23,59));
        String body=mvc.perform(post("/api/v1/dashboard-maintenance/today-shipments")
                        .header("Authorization",bearer(token)).header("Idempotency-Key","return-create")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id=json.readTree(body).get("id").asText();
        String path="/api/v1/dashboard-maintenance/today-shipments/"+id+"/transfer-to-pending";
        mvc.perform(post(path).header("Authorization",bearer(reader)).header("Idempotency-Key","return-denied")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"补充计划\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).header("Authorization",bearer(token)).header("Idempotency-Key","return-no-reason")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path).header("Authorization",bearer(token)).header("Idempotency-Key","return-stale")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":99,\"reason\":\"补充计划\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dashboard_pending_shipment",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM dashboard_today_shipment WHERE id=?",String.class,id)).isEqualTo("ACTIVE");
        String transferred=mvc.perform(post(path).header("Authorization",bearer(token)).header("Idempotency-Key","return-ok")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"补充计划\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(7))
                .andExpect(jsonPath("$.boxCount").value(3)).andExpect(jsonPath("$.productId").value(productId))
                .andExpect(jsonPath("$.plannedShipDate").value(today.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        String targetId=json.readTree(transferred).get("id").asText();
        mvc.perform(post(path).header("Authorization",bearer(token)).header("Idempotency-Key","return-ok")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"补充计划\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(targetId));
        mvc.perform(post(path).header("Authorization",bearer(token)).header("Idempotency-Key","return-again")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"再次转移\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dashboard_pending_shipment",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM dashboard_today_shipment WHERE id=?",String.class,id)).isEqualTo("ARCHIVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='DASHBOARD_TODAY_TRANSFER_PENDING'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT detail FROM audit_event WHERE action='DASHBOARD_TODAY_TRANSFER_PENDING'",String.class)).contains("补充计划");
        for(int cycle=0;cycle<3;cycle++){
            long pendingVersion=jdbc.queryForObject("SELECT version FROM dashboard_pending_shipment WHERE id=?",Long.class,targetId);
            var transfer=Map.of("quantity",5,"boxCount",3,"remark","更新数量", "latestShipTime",today.atTime(23,59).toString(),"version",pendingVersion);
            mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments/{id}/transfer-to-today",targetId)
                            .header("Authorization",bearer(token)).header("Idempotency-Key","round-trip-out-"+cycle)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(transfer)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
            long todayVersion=jdbc.queryForObject("SELECT version FROM dashboard_today_shipment WHERE id=?",Long.class,id);
            mvc.perform(post(path).header("Authorization",bearer(token)).header("Idempotency-Key","round-trip-back-"+cycle)
                            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version",todayVersion,"reason","延期"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(targetId)).andExpect(jsonPath("$.quantity").value(5));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dashboard_pending_shipment",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dashboard_today_shipment",Integer.class)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='DASHBOARD_PENDING_SHIPMENT_RETURN'",Integer.class)).isEqualTo(3);
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
    void transfersShipmentImageExposesHistoryAndRestrictsArchivedImageToManagers() throws Exception {
        String productId=createProduct("DASH-IMG","带图发货产品");
        String productToken=user("image-manager","PRODUCT_MATERIAL");
        String selectionToken=user("image-reader","SELECTION");
        MockMultipartFile file=new MockMultipartFile("file","shipment.png","image/png",png());
        String upload=mvc.perform(multipart("/api/v1/dashboard-images").file(file).param("purpose","SHIPMENT")
                        .header("Authorization",bearer(productToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String imageId=json.readTree(upload).get("id").asText();
        LocalDate tomorrow=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1);
        Map<String,Object> pending=new LinkedHashMap<>(shipment(tomorrow,productId,7,3,
                LocalDateTime.of(tomorrow,java.time.LocalTime.of(18,0))));pending.put("imageId",imageId);
        String created=mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments")
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","with-image")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(pending)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String pendingId=json.readTree(created).get("id").asText();
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        Map<String,Object> transfer=Map.of("quantity",7,"boxCount",3,"remark","带图转入","imageId",imageId,
                "latestShipTime",LocalDateTime.of(today,java.time.LocalTime.of(18,0)).toString(),"version",0);
        String transferred=mvc.perform(post("/api/v1/dashboard-maintenance/pending-shipments/{id}/transfer-to-today",pendingId)
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","with-image-transfer")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(transfer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imageId").value(imageId))
                .andReturn().getResponse().getContentAsString();
        String todayId=json.readTree(transferred).get("id").asText();
        assertThat(jdbc.queryForMap("SELECT attached_resource_type,attached_resource_id FROM dashboard_image_asset WHERE id=?",imageId))
                .containsEntry("ATTACHED_RESOURCE_TYPE","TODAY_SHIPMENT").containsEntry("ATTACHED_RESOURCE_ID",todayId);
        mvc.perform(get("/api/v1/dashboard-maintenance/pending-shipments/{id}/history",pendingId)
                        .header("Authorization",bearer(productToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("DASHBOARD_PENDING_SHIPMENT_TRANSFER"));
        mvc.perform(post("/api/v1/dashboard-maintenance/today-shipments/{id}/archive",todayId)
                        .header("Authorization",bearer(productToken)).header("Idempotency-Key","archive-image-today")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"验收归档\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard-images/{id}/thumbnail",imageId).header("Authorization",bearer(selectionToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/dashboard-images/{id}/thumbnail",imageId).header("Authorization",bearer(productToken)))
                .andExpect(status().isOk());
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
    private void clean(){for(String table:new String[]{"dashboard_shipment_dispatch","dashboard_shipment_document","dashboard_command","dashboard_today_shipment","dashboard_pending_shipment",
            "dashboard_pending_product","dashboard_image_asset","product_auxiliary_material","variant_attribute","product_variant","product",
            "iam_password_reset_challenge","audit_event","iam_session","iam_user"}){jdbc.update("DELETE FROM "+table);}}
}
