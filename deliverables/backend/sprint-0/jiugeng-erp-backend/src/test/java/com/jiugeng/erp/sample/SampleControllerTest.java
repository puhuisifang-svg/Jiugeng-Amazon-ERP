package com.jiugeng.erp.sample;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SampleControllerTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
 @AfterEach void clean(){jdbc.update("DELETE FROM sample_location");}
 @Test void validatesSkuAndPermissions()throws Exception{
  var ids=sku();String writer=user("PRODUCT_MATERIAL"),reader=user("SELECTION");
  mvc.perform(get("/api/v1/samples")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/samples").header("Authorization",reader)).andExpect(status().isOk());
  create(ids,"A-01",reader,403);create(ids,"  ",writer,400);create(List.of(ids.get(0),UUID.randomUUID().toString()),"A-01",writer,400);
  mvc.perform(get("/api/v1/samples").param("page","-1").header("Authorization",writer)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/samples").param("size","1000").header("Authorization",writer)).andExpect(status().isBadRequest());
 }
 @Test void preventsDuplicateAndLostUpdatesAndRemovesOnlyLocation()throws Exception{
  var ids=sku();String token=user("ADMIN");JsonNode row=create(ids," A-01 ",token,200);String id=row.path("id").asText();assertThat(row.path("locationCode").asText()).isEqualTo("A-01");
  create(ids,"a-01",token,409);create(ids,"Ａ-01",token,409);create(ids,"A-02",token,200);
  var payload=new LinkedHashMap<String,Object>();payload.put("productId",ids.get(0));payload.put("variantId",ids.get(1));payload.put("locationCode","A-03");payload.put("version",0);
  mvc.perform(put("/api/v1/samples/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(put("/api/v1/samples/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isConflict());
  mvc.perform(delete("/api/v1/samples/"+id).param("version","0").header("Authorization",token)).andExpect(status().isConflict());
  mvc.perform(delete("/api/v1/samples/"+id).param("version","1").header("Authorization",token)).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE id=?",Integer.class,ids.get(1))).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action IN ('SAMPLE_CREATE','SAMPLE_UPDATE','SAMPLE_REMOVE')",Integer.class,id)).isEqualTo(3);
 }
 @Test void supportsSearchAndPaginationWithoutWildcardExpansion()throws Exception{
  var ids=sku();String token=user("ADMIN");for(int i=0;i<12;i++)create(ids,"SHELF-"+i,token,200);
  mvc.perform(get("/api/v1/samples").param("q","shelf").param("size","10").param("page","1").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(12)).andExpect(jsonPath("$.items.length()").value(2));
  mvc.perform(get("/api/v1/samples").param("q","%").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
 }
 private JsonNode create(List<String> ids,String location,String token,int expected)throws Exception{return json.readTree(mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("productId",ids.get(0),"variantId",ids.get(1),"locationCode",location)))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());}
 private List<String> sku(){String p=UUID.randomUUID().toString(),v=UUID.randomUUID().toString();jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,product_name,status,created_by,updated_by) VALUES(?,?,?,?,?,'ACTIVE','test','test')",p,p,p,"Synthetic sample product","Synthetic sample product");jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,attribute_signature,status) VALUES(?,?,?,?,?,'SYNTHETIC','ACTIVE')",v,p,v,v,"Synthetic sample SKU");return List.of(p,v);}
 private String user(String role)throws Exception{String id=UUID.randomUUID().toString();String password="Synthetic-Sample-Test-42!";jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic sample QA",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();}
}
