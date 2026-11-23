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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:material-closeout;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaterialCloseoutTest {
    private static final String PASSWORD = "Material-Test-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    private com.fasterxml.jackson.databind.JsonNode save(String token,Map<String,Object> input) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/v1/materials/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private Map<String,Object> command(Map<String,Object> material){var value=new LinkedHashMap<String,Object>();value.put("requestId",java.util.UUID.randomUUID().toString());value.put("material",material);value.put("suppliers",java.util.List.of());return value;}
    private String admin() throws Exception{return createUserAndLogin("m-"+java.util.UUID.randomUUID().toString().substring(0,8),"ADMIN");}
    @Test void aggregateRetryVersionAndSupplierFailureAreAtomic() throws Exception {
        String token=admin();var input=command(material("AGG-ATOMIC","原子保存测试"));
        input.put("suppliers",java.util.List.of(Map.of("supplierId",java.util.UUID.randomUUID().toString(),"primary",true)));
        mvc.perform(post("/api/v1/materials/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auxiliary_material WHERE material_code='AGG-ATOMIC'",Integer.class)).isZero();
        input.put("suppliers",java.util.List.of());var created=save(token,input);assertThat(save(token,input).get("id")).isEqualTo(created.get("id"));
        String id=created.get("id").asText();var edit=command(material("AGG-ATOMIC","最新名称"));edit.put("id",id);edit.put("version",created.get("version").asLong());save(token,edit);
        edit.put("requestId",java.util.UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/materials/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(edit))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATERIAL_VERSION_CONFLICT"));
        mvc.perform(get("/api/v1/materials/manage/{id}/history",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.total").isNumber());
    }
    @Test void measuredStockIsExactAndUnitCannotChangeAfterUse() throws Exception {
        String token=admin();var master=material("MEASURE-TEST","计量库存测试");master.put("unit","米");var saved=save(token,command(master));String id=saved.get("id").asText();
        var stock=new LinkedHashMap<String,Object>(Map.of("requestId",java.util.UUID.randomUUID().toString(),"materialId",id,"type","INBOUND","quantity","1.2345","unit","米","reason","小数计量测试"));
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/inventory/transactions").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(stock))).andExpect(status().isOk()).andExpect(jsonPath("$.balance.quantity").value(1.2345));
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_balance WHERE auxiliary_material_id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("1.2345");
        master.put("unit","件");var edit=command(master);edit.put("id",id);edit.put("version",saved.get("version").asLong());
        mvc.perform(post("/api/v1/materials/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(edit))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MATERIAL_UNIT_LOCKED"));
        stock.put("requestId",java.util.UUID.randomUUID().toString());stock.put("quantity","0.00001");
        mvc.perform(post("/api/v1/inventory/transactions").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(stock))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/materials/manage/{id}/inventory",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.ledger.totalCount").value(1));
    }
    @Test void integerUnitRejectsFractionsAndSafetyCanInitializeBalance() throws Exception {
        String token=admin();var saved=save(token,command(material("COUNT-TEST","计数测试")));String id=saved.get("id").asText();
        mvc.perform(put("/api/v1/inventory/balances/{id}/safety-stock",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("safetyStock",3)))).andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0));
        mvc.perform(post("/api/v1/inventory/transactions").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId",java.util.UUID.randomUUID().toString(),"materialId",id,"type","INBOUND","quantity","1.5","unit","PCS","reason","禁止半件")))).andExpect(status().isBadRequest());
    }
    @Test void pageFiltersAndExportsRespectPermissions() throws Exception {
        String token=admin();for(int i=0;i<11;i++)save(token,command(material("PAGE-"+i,"分页合成测试"+i)));
        mvc.perform(get("/api/v1/materials/manage/page").param("q","分页合成测试").param("page","1").param("size","10").header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalCount").value(11));
        String reader=createUserAndLogin("r-"+java.util.UUID.randomUUID().toString().substring(0,8),"SELECTION");
        mvc.perform(get("/api/v1/materials/manage/page").param("price","MISSING").header("Authorization",bearer(reader))).andExpect(status().isForbidden());
        var content=mvc.perform(post("/api/v1/materials/manage/export").header("Authorization",bearer(reader)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("filters",Map.of("q","分页合成测试"),"ids",java.util.List.of())))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(content).contains("分页合成测试").doesNotContain("成本价");
        assertThat(MaterialManagementController.csv("=1+1")).isEqualTo("\"'=1+1\"");
        assertThat(MaterialManagementController.csv("=1+1\nnext")).startsWith("\"'=1+1");
    }
    @Test void retainedDisabledSupplierCanBeSavedButCannotBeNewlyLinked() throws Exception {
        String token=admin();String response=mvc.perform(post("/api/v1/suppliers").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name","合成历史供应商","sourceUrl","https://test.1688.com","categories",java.util.List.of("MATERIAL"))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String supplier=objectMapper.readTree(response).get("id").asText();var links=java.util.List.of(Map.of("supplierId",supplier,"primary",true));var first=command(material("HISTORY-SUPPLIER","历史关联测试"));first.put("suppliers",links);var saved=save(token,first);
        jdbc.update("UPDATE supplier SET status='DISABLED' WHERE id=?",supplier);
        var edit=command(material("HISTORY-SUPPLIER","保留历史关联"));edit.put("id",saved.get("id").asText());edit.put("version",saved.get("version").asLong());edit.put("suppliers",links);save(token,edit);
        mvc.perform(get("/api/v1/materials/manage/{id}",saved.get("id").asText()).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.suppliers[0].supplierName").value("合成历史供应商"));
        var other=command(material("HISTORY-NEW","新关联应拒绝"));other.put("suppliers",links);
        mvc.perform(post("/api/v1/materials/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(other))).andExpect(status().isConflict());
    }
    @Test void copyOwnsIndependentImageAndBatchDeleteRemainsAtomic() throws Exception {
        String token=admin();var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
        String receipt=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/catalog-images").file(new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray())).param("purpose","MATERIAL_IMAGE").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String image=objectMapper.readTree(receipt).get("id").asText();var input=material("COPY-ORIGINAL","图片来源");input.remove("imageUrl");input.put("imageAssetId",image);var original=save(token,command(input));
        input.put("materialCode","COPY-NEW");input.put("name","独立副本");var copy=command(input);copy.put("copyFromId",original.get("id").asText());var copied=save(token,copy);
        assertThat(copied.get("imageAssetId").asText()).isNotEqualTo(image);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT original_storage_key) FROM catalog_image_asset WHERE id IN (?,?)",Integer.class,image,copied.get("imageAssetId").asText())).isEqualTo(2);
        var targets=Map.of("items",java.util.List.of(Map.of("id",original.get("id").asText(),"version",original.get("version").asLong()),Map.of("id",copied.get("id").asText(),"version",999)));
        mvc.perform(post("/api/v1/materials/manage/delete").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(targets))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auxiliary_material WHERE material_code IN ('COPY-ORIGINAL','COPY-NEW')",Integer.class)).isEqualTo(2);
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
                """, id, loginName, loginName, encoder.encode(PASSWORD), role);jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginName", loginName, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
}
