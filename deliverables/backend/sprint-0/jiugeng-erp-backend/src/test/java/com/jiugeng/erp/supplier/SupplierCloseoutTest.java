package com.jiugeng.erp.supplier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:supplier-closeout;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1") @AutoConfigureMockMvc @ActiveProfiles("test")
class SupplierCloseoutTest {
 private static final String PASSWORD="Supplier-Test-42"; // gitleaks:allow -- synthetic credential for isolated H2 test users only
 @Autowired MockMvc mvc; @Autowired ObjectMapper objectMapper; @Autowired JdbcTemplate jdbc;
 private String admin() throws Exception{return createUserAndLogin("s-"+UUID.randomUUID().toString().substring(0,8),"ADMIN");}
 private Map<String,Object> master(String name){return new LinkedHashMap<>(Map.of("name",name,"wechatId","synthetic_wechat","contactPerson","合成联系人","remark","第一行\n第二行","status","ACTIVE","categories",List.of("MATERIAL","PRODUCT")));}
 private Map<String,Object> command(Map<String,Object> supplier){var m=new LinkedHashMap<String,Object>();m.put("requestId",UUID.randomUUID().toString());m.put("supplier",supplier);return m;}
 private JsonNode save(String token,Map<String,Object> input)throws Exception{return objectMapper.readTree(mvc.perform(post("/api/v1/suppliers/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void saveRetryPayloadAndVersionAreProtected() throws Exception{
  String token=admin();var master=master("稳定原请求");var cmd=command(master);var created=save(token,cmd);String id=created.get("id").asText();assertThat(save(token,cmd).get("id").asText()).isEqualTo(id);
  master.put("name","修改载荷");mvc.perform(post("/api/v1/suppliers/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(cmd))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLIER_REQUEST_CONFLICT"));
  cmd=command(master);cmd.put("id",id);master.put("version",0);save(token,cmd);cmd.put("requestId",UUID.randomUUID().toString());mvc.perform(post("/api/v1/suppliers/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(cmd))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLIER_VERSION_CONFLICT"));
  mvc.perform(get("/api/v1/suppliers/manage/{id}/history",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
  assertThat(jdbc.queryForObject("SELECT remark FROM supplier WHERE id=?",String.class,id)).isEqualTo("第一行\n第二行");
 }
 @Test void categoryReductionNeedsExplicitImpactConfirmation() throws Exception{
  String token=admin();var master=master("影响确认");var created=save(token,command(master));master.put("categories",List.of("MATERIAL"));master.put("version",0);var cmd=command(master);cmd.put("id",created.get("id").asText());
  mvc.perform(post("/api/v1/suppliers/manage/save").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(cmd))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLIER_IMPACT_CONFIRM_REQUIRED"));cmd.put("impactConfirmed",true);save(token,cmd);
  mvc.perform(post("/api/v1/suppliers/manage/{id}/status",created.get("id").asText()).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status","DISABLED","version",1)))).andExpect(status().isConflict());
 }
 @Test void searchPaginationAndCsvUseCompleteResult() throws Exception{
  String token=admin();for(int i=0;i<11;i++)save(token,command(master("供应分页合成"+i)));
  mvc.perform(get("/api/v1/suppliers").param("q","供应分页合成").param("page","1").param("size","10").header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalCount").value(11));
  mvc.perform(get("/api/v1/suppliers").param("q","%_").header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
  var result=mvc.perform(post("/api/v1/suppliers/manage/export").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("q","供应分页合成")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(result).contains("供应分页合成10","合成联系人","第一行\n第二行");assertThat(SupplierManagementController.csv("=1+1\nnext")).startsWith("\"'=1+1");
 }
 @Test void purchaseRelationsIncludeStoredItemNamesAndKeepOrderIdentity() throws Exception{
  String token=admin();String supplier=save(token,command(master("采购名称合成供应商"))).path("id").asText();
  for(int count=0;count<3;count++){
   String order=UUID.randomUUID().toString(),number="SYN-"+count+"-"+order.substring(0,8);
   jdbc.update("INSERT INTO purchase_order(id,order_no,order_type,supplier_id,status,currency,created_by,updated_by) VALUES(?,?,'MATERIAL',?,'SUBMITTED','CNY','synthetic','synthetic')",order,number,supplier);
   for(int line=1;line<=count;line++)jdbc.update("INSERT INTO purchase_order_line(id,order_id,line_no,item_id,item_code,item_name,unit,ordered_quantity,unit_price) VALUES(?,?,?,?,?,?,'件',1,1)",UUID.randomUUID().toString(),order,line,UUID.randomUUID().toString(),"SYN-ITEM-"+line,"合成采购商品"+line);
  }
  mvc.perform(get("/api/v1/suppliers/manage/{id}/relations",supplier).param("type","PURCHASE").header("Authorization",bearer(token)))
   .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(3))
   .andExpect(jsonPath("$.items[0].purchaseName").value("未命名采购"))
   .andExpect(jsonPath("$.items[1].purchaseName").value("合成采购商品1"))
   .andExpect(jsonPath("$.items[2].purchaseName").value("合成采购商品1 等 2 项"))
   .andExpect(jsonPath("$.items[2].code").value(org.hamcrest.Matchers.startsWith("SYN-2-")))
   .andExpect(jsonPath("$.items[2].name").value("SUBMITTED"));
 }
 @Test void relationPagesEnforceDomainReadPermissions() throws Exception{
  String token=admin();String id=save(token,command(master("关联权限"))).get("id").asText();
  // Synthetic role deliberately grants supplier read only, without other domains or delete.
  String role="SUP_TEST_"+UUID.randomUUID().toString().substring(0,8);jdbc.update("INSERT INTO iam_role(id,name,kind,status,version) VALUES(?,?,'CUSTOM','ENABLED',0)",role,role);jdbc.update("INSERT INTO iam_role_permission(role_id,permission_code) VALUES(?,'suppliers.read')",role);
  String reader=createUserAndLogin("r-"+UUID.randomUUID().toString().substring(0,8),role);
  mvc.perform(get("/api/v1/suppliers/manage/{id}/impact",id).header("Authorization",bearer(reader))).andExpect(status().isOk()).andExpect(jsonPath("$.counts").isEmpty());
  mvc.perform(get("/api/v1/suppliers/manage/{id}/relations",id).param("type","PRODUCT").header("Authorization",bearer(reader))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/suppliers/manage/{id}/relations",id).param("type","PURCHASE").header("Authorization",bearer(reader))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/suppliers/manage/{id}/relations",id).param("type","MATERIAL").header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
 }
 @Test void copiedContactImageHasIndependentStorage() throws Exception{
  String token=admin();var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  String receipt=mvc.perform(multipart("/api/v1/catalog-images").file(new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray())).param("purpose","SUPPLIER_QR").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();String image=objectMapper.readTree(receipt).get("id").asText();var master=master("图片来源");master.put("qrImageId",image);var source=save(token,command(master));master.put("name","独立副本");var cmd=command(master);cmd.put("copyFromId",source.get("id").asText());var copy=save(token,cmd);assertThat(copy.get("qrImageId").asText()).isNotEqualTo(image);assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT original_storage_key) FROM catalog_image_asset WHERE id IN (?,?)",Integer.class,image,copy.get("qrImageId").asText())).isEqualTo(2);
 }
    private String createUserAndLogin(String loginName, String role) throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        jdbc.update("""
                INSERT INTO iam_user
                    (id, login_name, display_name, password_hash, role, status, must_change_password)
                VALUES (?, ?, ?, ?, ?, 'ENABLED', FALSE)
                """, id, loginName, loginName, encoder.encode(PASSWORD), role.equals("ADMIN")?"ADMIN":"SELECTION");jdbc.update("INSERT INTO iam_user_role(user_id,role_id) VALUES(?,?)",id,role);
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginName", loginName, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
}
