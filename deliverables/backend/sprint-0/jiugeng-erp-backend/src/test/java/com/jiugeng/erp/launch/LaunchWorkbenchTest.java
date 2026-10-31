package com.jiugeng.erp.launch;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import java.util.*;
import java.time.LocalDate;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class LaunchWorkbenchTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
 @Test void developmentReadinessUsesTransitionGatesWithoutMutatingProject()throws Exception{
  String admin=user("ADMIN"),id=post("/development/workbench",Map.of("title","Synthetic stage guidance","owner","Developer","reason","test","requestId",UUID.randomUUID().toString()),admin,200).path("id").asText(),url="/development/workbench/"+id;
  JsonNode w=get(url,admin).path("workspace");JsonNode checks=get(url+"/readiness",admin);assertThat(checks.path("action").asText()).isEqualTo("SUBMIT");assertThat(checks.path("issues").isEmpty()).isFalse();
  w=save(url,w,completeDoc(),admin,200);w=action(url,w,"REVIEW_RISK","Reviewed",admin,200);w=action(url,w,"SUBMIT","Ready",admin,200);w=action(url,w,"APPROVE","Approved",admin,200);
  checks=get(url+"/readiness",admin);assertThat(checks.path("action").asText()).isEqualTo("START");assertThat(checks.path("issues").isEmpty()).isTrue();
  w=action(url,w,"START","",admin,200);assertThat(get(url+"/readiness",admin).path("issues").isEmpty()).isTrue();w=action(url,w,"NEXT","",admin,200);
  checks=get(url+"/readiness",admin);assertThat(checks.path("issues").toString()).contains("供应报价");assertThat(checks.path("version").asLong()).isEqualTo(w.path("version").asLong());
  action(url,w,"NEXT","",admin,400);assertThat(get(url,admin).path("workspace").path("version").asLong()).isEqualTo(w.path("version").asLong());
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1"+url+"/readiness")).andExpect(status().isUnauthorized());
 }
 @Test void developmentListFiltersOwnerAndStageWithConsistentCounts()throws Exception{
  String admin=user("ADMIN"),owner="owner-"+UUID.randomUUID(),name="Synthetic-project-"+UUID.randomUUID();
  String id=post("/development/workbench",Map.of("title",name,"owner",owner,"reason","test","requestId",UUID.randomUUID().toString()),admin,200).path("id").asText();
  JsonNode list=get("/development/workbench?q="+name+"&owner="+owner+"&stage=0&state=DRAFT",admin);
  assertThat(list.path("total").asInt()).isEqualTo(1);assertThat(list.path("counts").path("DRAFT").asInt()).isEqualTo(1);assertThat(list.path("items").get(0).path("id").asText()).isEqualTo(id);assertThat(list.path("items").get(0).path("hasProposal").asBoolean()).isFalse();
  assertThat(get("/development/workbench?q="+name+"&stage=2",admin).path("total").asInt()).isZero();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/development/workbench?stage=5").header("Authorization",admin)).andExpect(status().isBadRequest());
 }
 @Test void candidateFiltersCountsAndObservationStayConsistent()throws Exception{
  String admin=user("ADMIN"),prefix="Synthetic-filter-"+UUID.randomUUID(),owner="owner-"+UUID.randomUUID();
  String first=post("/selection/workbench",Map.of("requestId",UUID.randomUUID().toString(),"type","OTHER","title",prefix+"-one","owner",owner,"reason","filter test"),admin,200).path("id").asText();
  String second=post("/selection/workbench",Map.of("requestId",UUID.randomUUID().toString(),"type","OTHER","title",prefix+"-two","owner",owner,"reason","filter test"),admin,200).path("id").asText();
  JsonNode w=get("/selection/workbench/"+first,admin).path("workspace");action("/selection/workbench/"+first,w,"START","",admin,200);
  JsonNode filtered=get("/selection/workbench?q="+prefix+"&source=OTHER&owner="+owner+"&state=EVALUATING",admin);
  assertThat(filtered.path("total").asInt()).isEqualTo(1);assertThat(filtered.path("items").get(0).path("id").asText()).isEqualTo(first);
  assertThat(filtered.path("counts").path("NEW").asInt()).isEqualTo(1);assertThat(filtered.path("counts").path("EVALUATING").asInt()).isEqualTo(1);
  assertThat(filtered.path("owners").toString()).contains(owner);assertThat(filtered.path("items").get(0).has("observation")).isTrue();
  assertThat(get("/selection/workbench?q="+prefix+"&source=AMAZON",admin).path("total").asInt()).isZero();
  jdbc.update("DELETE FROM launch_workspace WHERE kind='CANDIDATE' AND entity_id=?",second);jdbc.update("UPDATE selection_candidate SET status='REVIEWING' WHERE id=?",second);
  assertThat(get("/selection/workbench?q="+prefix+"&state=EVALUATING",admin).path("total").asInt()).isEqualTo(2);
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/selection/workbench?source=invalid").header("Authorization",admin)).andExpect(status().isBadRequest());
 }
 @Test void savedIdentityIsSearchableInBothLists()throws Exception{
  String admin=user("ADMIN");
  for(String root:List.of("/selection/workbench","/development/workbench")){
   String request=UUID.randomUUID().toString();String id=post(root,Map.of("requestId",request,"type","OTHER","title","Synthetic original","owner","Before","reason","Synthetic identity regression"),admin,200).path("id").asText();
   JsonNode w=get(root+"/"+id,admin).path("workspace");ObjectNode d=w.path("document").deepCopy();String name="Synthetic-renamed-"+request;d.put("name",name).put("owner","After");save(root+"/"+id,w,d,admin,200);
   JsonNode found=get(root+"?q="+name,admin);assertThat(found.path("total").asInt()).isEqualTo(1);assertThat(found.path("items").get(0).path("owner").asText()).isEqualTo("After");
  }
 }
 @Test void nonAmazonCandidateRiskGatesAndAtomicDevelopmentHandoff()throws Exception{
  String admin=user("ADMIN"),writer=user("SELECTION"),reader=user("PRODUCT_MATERIAL"),id=UUID.randomUUID().toString();
  post("/selection/workbench",Map.of("requestId",id,"type","OTHER","title","Synthetic folding concept","owner","Researcher","reason","Synthetic customer evidence"),writer,200);
  JsonNode w=get("/selection/workbench/"+id,writer).path("workspace");
  w=action("/selection/workbench/"+id,w,"START","",writer,200);
  ObjectNode d=completeDoc();
  w=save("/selection/workbench/"+id,w,d,writer,200);
  action("/selection/workbench/"+id,w,"SUBMIT","",writer,400);
  action("/selection/workbench/"+id,w,"REVIEW_RISK","Synthetic review",writer,403);
  w=action("/selection/workbench/"+id,w,"REVIEW_RISK","Synthetic scope reviewed",admin,200);
  w=action("/selection/workbench/"+id,w,"SUBMIT","Ready",writer,200);
  save("/selection/workbench/"+id,w,d,writer,400);
  action("/selection/workbench/"+id,w,"APPROVE","Approved",writer,403);
  w=action("/selection/workbench/"+id,w,"APPROVE","Approved synthetic opportunity",admin,200);
  assertThat(w.path("state").asText()).isEqualTo("APPROVED");
  // Candidate approval is sufficient for production visibility even before development starts.
  assertThat(get("/dashboard/production-products?keyword=Synthetic",admin).path("items").findValuesAsText("developmentId")).contains(id);
  JsonNode dev=get("/development/workbench/"+id,reader).path("workspace");
  assertThat(dev.path("baseline").path("needs").get(0).path("pain").asText()).isEqualTo("Storage");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_development WHERE candidate_id=?",Integer.class,id)).isEqualTo(1);
  action("/selection/workbench/"+id,w,"APPROVE","again",admin,400);
  mvc.perform(put("/api/v1/selection/candidates/"+id+"/research").header("Authorization",writer).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isConflict());
  dev=action("/development/workbench/"+id,dev,"START","",reader,200);
  ObjectNode changed=dev.path("document").deepCopy();changed.put("price",15).put("changeReason","Synthetic new cost scenario");
  dev=save("/development/workbench/"+id,dev,changed,reader,200);
  assertThat(dev.path("proposal").path("price").asInt()).isEqualTo(15);assertThat(dev.path("document").path("price").asInt()).isEqualTo(20);
  action("/development/workbench/"+id,dev,"NEXT","",reader,400);
  dev=action("/development/workbench/"+id,dev,"CHANGE_APPROVE","Accept change",admin,200);
  assertThat(dev.path("document").path("price").asInt()).isEqualTo(15);assertThat(dev.path("riskReview").path("valid").asBoolean()).isFalse();
  action("/development/workbench/"+id,dev,"COMPLETE","Cannot skip stages",admin,400);
 }
 @Test void manualProjectCompletesOnlyWithCurrentSampleRiskAndActiveSku()throws Exception{
  String admin=user("ADMIN"),writer=user("PRODUCT_MATERIAL");String id=post("/development/workbench",Map.of("title","Synthetic project","owner","Developer","reason","test","requestId",UUID.randomUUID().toString()),writer,200).path("id").asText();String url="/development/workbench/"+id;
  JsonNode w=get(url,writer).path("workspace");ObjectNode d=completeDoc();w=save(url,w,d,writer,200);w=action(url,w,"REVIEW_RISK","Review",admin,200);w=action(url,w,"SUBMIT","Manual project",writer,200);w=action(url,w,"APPROVE","Approved",admin,200);w=action(url,w,"START","",writer,200);
  w=action(url,w,"NEXT","",writer,200);action(url,w,"NEXT","",writer,400);
  d=w.path("document").deepCopy();d.putArray("quotes").addObject().put("id","q1").put("supplier","Synthetic supplier").put("amount",8).put("currency","USD").put("basis","Synthetic quote").put("validUntil",LocalDate.now().plusDays(30).toString()).put("specVersion","1");d.put("selectedQuote","q1").put("changeReason","Select supply proposal");
  w=save(url,w,d,writer,200);w=action(url,w,"CHANGE_APPROVE","Supply accepted",admin,200);
  d=w.path("document").deepCopy();String revision=d.path("specVersion").asText();((ObjectNode)d.path("quotes").get(0)).put("specVersion",revision);
  d.putArray("samples").addObject().put("id","s1").put("quoteId","q1").put("specVersion",revision).put("result","PASS").put("receivedDate",LocalDate.now().toString()).put("tester","Synthetic tester").put("evidence","Synthetic test report");d.put("selectedSample","s1").put("riskVersion",revision);
  ((ObjectNode)d.path("requirements").get(0)).put("result","PASS").put("testedVersion",revision).put("evidence","Synthetic measured result");
  String product=UUID.randomUUID().toString(),sku=UUID.randomUUID().toString();jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,product_name,status,created_by,updated_by) VALUES(?,?,?,?,?,'ACTIVE','test','test')",product,product,product,"Synthetic outcome","Synthetic outcome");
  jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,attribute_signature,status) VALUES(?,?,?,?,?,'SYNTHETIC','ACTIVE')",sku,product,sku,sku,"Synthetic SKU");d.put("productId",product);d.putArray("skus").addObject().put("id","sku-row").put("skuId",sku);d.putArray("handover").addObject().put("id","handover").put("name","Final specification").put("evidence","Synthetic record").put("owner","Developer");
  w=save(url,w,d,writer,200);w=action(url,w,"REVIEW_RISK","Final scheme checked",admin,200);
  for(int i=0;i<4;i++)w=action(url,w,"NEXT","",writer,200);
  w=action(url,w,"SUBMIT","Ready for acceptance",writer,200);
  jdbc.update("UPDATE product SET status='DISABLED' WHERE id=?",product);action(url,w,"COMPLETE","Must not accept inactive product",admin,400);jdbc.update("UPDATE product SET status='ACTIVE' WHERE id=?",product);
  w=action(url,w,"COMPLETE","Complete",admin,200);assertThat(w.path("state").asText()).isEqualTo("COMPLETED");save(url,w,d,writer,400);
 }
 @Test void riskDoubtsAndExpiredAuthorizationBlockReview()throws Exception{
  String admin=user("ADMIN"),id=UUID.randomUUID().toString();post("/selection/workbench",Map.of("requestId",id,"type","OTHER","title","Synthetic risk","owner","Reviewer","reason","test"),admin,200);
  JsonNode w=get("/selection/workbench/"+id,admin).path("workspace");ObjectNode d=completeDoc();((ObjectNode)d.path("risks").get(0)).put("conclusion","DOUBT");w=save("/selection/workbench/"+id,w,d,admin,200);
  action("/selection/workbench/"+id,w,"REVIEW_RISK","cannot waive",admin,400);
  ((ObjectNode)d.path("risks").get(0)).put("conclusion","LICENSED").put("licenseScope","US").put("expires","2020-01-01");w=save("/selection/workbench/"+id,w,d,admin,200);action("/selection/workbench/"+id,w,"REVIEW_RISK","cannot waive expired license",admin,400);
  ObjectNode stale=json.createObjectNode().put("version",0);stale.set("document",d);mvc.perform(put("/api/v1/selection/workbench/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(stale.toString())).andExpect(status().isConflict());
 }
 @Test void legacyCreationCannotBypassNewRiskAndProjectGates()throws Exception{
  String admin=user("ADMIN");String id=post("/selection/candidates",Map.of("asinOrUrl","B0NEWGATE1","title","Synthetic gate"),admin,200).path("id").asText();
  post("/selection/candidates/"+id+"/transition",Map.of("version",0,"status","APPROVED"),admin,409);
  String project=post("/product-development",Map.of("requestId",UUID.randomUUID().toString(),"fields",Map.of("name","Synthetic manual")),admin,200).path("id").asText();
  post("/product-development/"+project+"/transition",Map.of("version",0,"target","IN_PROGRESS"),admin,409);
 }
 @Test void exactCostAndUnknownValidation(){ObjectNode d=completeDoc();assertThat(LaunchRules.cost(d).get("profit")).isEqualByComparingTo("6");assertThat(LaunchRules.cost(d).get("lowPriceProfit")).isEqualByComparingTo("4.6");assertThat(LaunchRules.cost(d).get("highCostProfit")).isEqualByComparingTo("5.2");((ObjectNode)d.path("costs").get(0)).putNull("amount");assertThatThrownBy(()->LaunchRules.cost(d)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 private ObjectNode completeDoc(){ObjectNode d=json.createObjectNode();d.put("name","Synthetic concept").put("owner","Developer").put("reason","Opportunity").put("customer","Customer").put("scenario","Scenario").put("conclusion","Proceed").put("market","US").put("salesMissingReason","Prototype: customer interview").put("price",20).put("commission",.15).put("advertising",.1).put("loss",.05).put("rateDate",LocalDate.now().toString()).put("costBasis","Synthetic").put("specVersion","1").put("riskVersion","1").put("startDate",LocalDate.now().toString()).put("endDate",LocalDate.now().plusDays(30).toString());
 d.putArray("needs").addObject().put("id","need-1").put("pain","Storage").put("evidence","Synthetic interview").put("validation","Test compactness");d.putArray("specs").addObject().put("id","spec-1").put("name","size").put("target","20cm");d.putArray("requirements").addObject().put("id","requirement-1").put("needId","need-1").put("priority","MUST").put("owner","Developer").put("proposal","Folding").put("standard","Fold test");
 ArrayNode risks=d.putArray("risks");for(String t:LaunchRules.RISK_TYPES)risks.addObject().put("id",t).put("type",t).put("market","US").put("scope","Synthetic scope").put("method","Synthetic search").put("evidence","https://example.com/test").put("reason","Test only").put("investigator","Reviewer").put("date",LocalDate.now().toString()).put("conclusion","CLEAR");
 ArrayNode costs=d.putArray("costs");for(String c:List.of("采购","包装","头程","履约","其他"))costs.addObject().put("id",c).put("category",c).put("currency","USD").put("rate",1).put("amount",c.equals("采购")?8:0).put("basis","Synthetic explicit amount");ArrayNode scores=d.putArray("criteria");for(int i=0;i<5;i++)scores.addObject().put("id","score-"+i).put("score",3).put("reason","Synthetic evidence");return d;}
 private JsonNode action(String url,JsonNode w,String a,String reason,String token,int expected)throws Exception{return post(url+"/action",Map.of("version",w.path("version").asLong(),"action",a,"reason",reason,"selfConfirmed",true),token,expected);}
 private JsonNode save(String url,JsonNode w,JsonNode d,String token,int expected)throws Exception{String response=mvc.perform(put("/api/v1"+url).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version",w.path("version").asLong(),"document",d)))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();return json.readTree(response);}
 private JsonNode post(String url,Object body,String token,int expected)throws Exception{String response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1"+url).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();return json.readTree(response);}
 private JsonNode get(String url,String token)throws Exception{return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1"+url).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 private String user(String role)throws Exception{String id=UUID.randomUUID().toString(),password=UUID.randomUUID().toString();jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic evaluator",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);return "Bearer "+post("/auth/login",Map.of("loginName",id,"password",password),"",200).path("token").asText();}
}
