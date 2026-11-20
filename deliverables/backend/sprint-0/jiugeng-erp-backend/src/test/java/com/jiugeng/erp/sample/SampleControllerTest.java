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
 @Test void manualSamplesAndParentFilter()throws Exception{
  String token=user("ADMIN");var ids=sku();create(ids,"P-01",token,200);
  var payload=new LinkedHashMap<String,Object>();payload.put("sampleName","Synthetic manual");payload.put("sampleCode","MAN-01");payload.put("locationCode","M-01");payload.put("specification","Synthetic dimensions");
  String id=json.readTree(mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isOk()).andExpect(jsonPath("$.productId").isEmpty()).andReturn().getResponse().getContentAsString()).path("id").asText();
  mvc.perform(get("/api/v1/samples").param("productId",ids.get(0)).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].variantId").value(ids.get(1)));
  mvc.perform(get("/api/v1/samples/parents").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(ids.get(0)));
  payload.put("version",0);payload.put("sampleName","Updated manual");
  mvc.perform(put("/api/v1/samples/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isOk()).andExpect(jsonPath("$.skuName").value("Updated manual"));
  payload.put("sampleName","");mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
 }
 @Test void storagePhotosArePermissionControlledAndReleased()throws Exception{
  String token=user("ADMIN"),reader=user("SELECTION");
  var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  var file=new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray());
  mvc.perform(multipart("/api/v1/catalog-images").file(file).param("purpose","SAMPLE_STORAGE").header("Authorization",reader)).andExpect(status().isForbidden());
  String image=json.readTree(mvc.perform(multipart("/api/v1/catalog-images").file(file).param("purpose","SAMPLE_STORAGE").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  String id=json.readTree(mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sampleName","Synthetic photo sample","locationCode","PHOTO-01","storageImageId",image)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  mvc.perform(get("/api/v1/catalog-images/"+image+"/original").header("Authorization",reader)).andExpect(status().isOk());
  mvc.perform(delete("/api/v1/samples/"+id).param("version","0").header("Authorization",token)).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("SELECT status FROM catalog_image_asset WHERE id=?",String.class,image)).isEqualTo("ACTIVE");
 }
 @Test void samplePicturesArePermissionControlledAndReleased()throws Exception{
  String token=user("ADMIN"),reader=user("SELECTION");
  var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  var file=new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray());
  mvc.perform(multipart("/api/v1/catalog-images").file(file).param("purpose","SAMPLE_IMAGE").header("Authorization",reader)).andExpect(status().isForbidden());
  String image=json.readTree(mvc.perform(multipart("/api/v1/catalog-images").file(file).param("purpose","SAMPLE_IMAGE").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  String id=json.readTree(mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sampleName","Synthetic photo sample","locationCode","PHOTO-01","sampleImageId",image)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  mvc.perform(get("/api/v1/catalog-images/"+image+"/original").header("Authorization",reader)).andExpect(status().isOk());
  mvc.perform(delete("/api/v1/samples/"+id).param("version","0").header("Authorization",token)).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("SELECT status FROM catalog_image_asset WHERE id=?",String.class,image)).isEqualTo("ACTIVE");
 }
 @Test void softRemovalRestoreAndSafeRetryUseNewSchema()throws Exception{
  String token=user("ADMIN");var ids=sku();String key=UUID.randomUUID().toString();
  String body=json.writeValueAsString(Map.of("sampleName","Synthetic retry","locationCode","R-01","remark","note searchable"));
  var request=post("/api/v1/samples").header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body);
  String id=json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  mvc.perform(post("/api/v1/samples").header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sample_location WHERE id=?",Integer.class,id)).isEqualTo(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='SAMPLE_CREATE'",Integer.class,id)).isEqualTo(1);
  mvc.perform(post("/api/v1/samples").header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body.replace("R-01","R-02"))).andExpect(status().isConflict());
  mvc.perform(get("/api/v1/samples").param("q","searchable").param("source","MANUAL").param("location","r-").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
  mvc.perform(delete("/api/v1/samples/"+id).param("version","0").header("Authorization",token)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/samples").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
  mvc.perform(get("/api/v1/samples").param("deleted","true").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
  mvc.perform(post("/api/v1/samples/"+id+"/restore").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isOk());
  mvc.perform(get("/api/v1/samples/"+id+"/history").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
  JsonNode linked=create(ids,"LINKED",token,200);String linkedId=linked.path("id").asText();
  mvc.perform(delete("/api/v1/samples/"+linkedId).param("version","0").header("Authorization",token)).andExpect(status().isOk());
  create(ids,"linked",token,200);
  mvc.perform(post("/api/v1/samples/"+linkedId+"/restore").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isConflict());
  assertThat(jdbc.queryForObject("SELECT deletion_key FROM sample_location WHERE id=?",String.class,linkedId)).isEqualTo(linkedId);
 }
 @Test void exportsProtectSpreadsheetCellsAndDoNotTruncateSelection()throws Exception{
  String token=user("ADMIN");String body=json.writeValueAsString(Map.of("sampleName","=formula\nnext","locationCode","CSV-01","remark","quoted \" text"));
  String id=json.readTree(mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  String csv=mvc.perform(get("/api/v1/samples/export").param("ids",id).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertThat(csv).contains("'=formula","quoted \"\" text");
  mvc.perform(post("/api/v1/samples/selection").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("ids",List.of(id,"missing"))))).andExpect(status().isBadRequest());
 }
 @Test void copiesImagesIndependentlyAndReturnsPresentationInOnePage()throws Exception{
  String token=user("ADMIN");var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3,3,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",bytes);
  var file=new org.springframework.mock.web.MockMultipartFile("file","synthetic.png","image/png",bytes.toByteArray());
  String image=json.readTree(mvc.perform(multipart("/api/v1/catalog-images").file(file).param("purpose","SAMPLE_IMAGE").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  String id=json.readTree(mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sampleName","Synthetic copy","locationCode","C-01","sampleImageId",image)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  var copy=json.readTree(mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("sampleName","Synthetic copy","locationCode","C-02","copyFromId",id)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  String copied=copy.path("displayImageId").asText();assertThat(copied).isNotBlank().isNotEqualTo(image);assertThat(copy.path("storageImageId").isNull()).isTrue();
  assertThat(jdbc.queryForObject("SELECT original_storage_key FROM catalog_image_asset WHERE id=?",String.class,copied)).isNotEqualTo(jdbc.queryForObject("SELECT original_storage_key FROM catalog_image_asset WHERE id=?",String.class,image));
  var ids=sku();create(ids,"LINKED-IMAGE",token,200);
  mvc.perform(get("/api/v1/samples").param("source","LINKED").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].currentSkuName").value("Synthetic sample SKU")).andExpect(jsonPath("$.items[0].imageSource").value("SNAPSHOT"));
 }
 private JsonNode create(List<String> ids,String location,String token,int expected)throws Exception{return json.readTree(mvc.perform(post("/api/v1/samples").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("productId",ids.get(0),"variantId",ids.get(1),"locationCode",location)))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());}
 private List<String> sku(){String p=UUID.randomUUID().toString(),v=UUID.randomUUID().toString();jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,product_name,status,created_by,updated_by) VALUES(?,?,?,?,?,'ACTIVE','test','test')",p,p,p,"Synthetic sample product","Synthetic sample product");jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,attribute_signature,status) VALUES(?,?,?,?,?,'SYNTHETIC','ACTIVE')",v,p,v,v,"Synthetic sample SKU");return List.of(p,v);}
 private String user(String role)throws Exception{String id=UUID.randomUUID().toString();String password="Synthetic-Sample-Test-42!";jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic sample QA",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();}
}
