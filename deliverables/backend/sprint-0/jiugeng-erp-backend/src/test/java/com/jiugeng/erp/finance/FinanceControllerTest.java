package com.jiugeng.erp.finance;
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
import org.springframework.mock.web.MockMultipartFile;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class FinanceControllerTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
 @AfterEach void clean(){jdbc.update("DELETE FROM finance_attachment");jdbc.update("DELETE FROM finance_bill");jdbc.update("DELETE FROM finance_account");jdbc.update("DELETE FROM finance_shop");}
 @Test void isolatesStoresAndCalculatesExactFilteredTotalsAndMonthlyAccounts()throws Exception{
  String token=user("ADMIN"),a=shop(token),b=shop(token);
  create(token,a,"INCOME","0.10","2026-01-01");create(token,a,"INCOME","0.20","2026-01-31");create(token,a,"EXPENSE","0.15","2026-02-01");create(token,b,"INCOME","999.00","2026-01-01");
  mvc.perform(get("/api/v1/finance/bills").param("shopId",a).param("from","2026-01-01").param("to","2026-12-31").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(3)).andExpect(jsonPath("$.totals[0].income").value(0.3)).andExpect(jsonPath("$.totals[0].balance").value(0.15)).andExpect(jsonPath("$.months[0].label").value("2026-01")).andExpect(jsonPath("$.months.length()").value(2)).andExpect(jsonPath("$.accounts[0].count").value(3));
  mvc.perform(get("/api/v1/finance/bills").param("shopId",a).param("from","2026-01-01").param("to","2026-01-31").param("q","%").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
 }
 @Test void rejectsUnauthorizedInvalidAmountsDatesAndCrossStoreEdits()throws Exception{
  String token=user("ADMIN"),denied=user("PRODUCT_MATERIAL"),s=shop(token),other=shop(token);
  mvc.perform(get("/api/v1/finance/shops").header("Authorization",denied)).andExpect(status().isForbidden());
  var in=input(s,"EXPENSE","1.001","2026-01-01");
  mvc.perform(post("/api/v1/finance/bills").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isBadRequest());
  in.put("amount","0");mvc.perform(post("/api/v1/finance/bills").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isBadRequest());
  String id=create(token,s,"EXPENSE","10.00","2026-01-01");in=input(other,"INCOME","20","2026-01-02");in.put("version",0);
  mvc.perform(put("/api/v1/finance/bills/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/finance/bills/"+id+"/void?version=0").header("Authorization",denied)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/finance/bills").param("shopId",s).param("from","2026-02-01").param("to","2026-01-01").header("Authorization",token)).andExpect(status().isBadRequest());
 }
 @Test void preservesVersionAuditAndVoidedHistoryAndExcludesVoidsFromTotals()throws Exception{
  String token=user("ADMIN"),s=shop(token),id=create(token,s,"INCOME","100","2026-01-01");var in=input(s,"INCOME","120","2026-01-01");in.put("version",0);
  mvc.perform(put("/api/v1/finance/bills/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(put("/api/v1/finance/bills/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isConflict());
  mvc.perform(post("/api/v1/finance/bills/"+id+"/void?version=0").header("Authorization",token)).andExpect(status().isConflict());
  mvc.perform(post("/api/v1/finance/bills/"+id+"/void?version=1").header("Authorization",token)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/finance/bills").param("shopId",s).param("from","2026-01-01").param("to","2026-12-31").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0)).andExpect(jsonPath("$.totals[0].income").value(0));
  mvc.perform(get("/api/v1/finance/bills/"+id+"/history").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_bill WHERE id=? AND voided=TRUE",Integer.class,id)).isEqualTo(1);
 }
 @Test void paginatesExportsAndProtectsAttachmentOwnershipAndTypes()throws Exception{
  String token=user("ADMIN"),s=shop(token),id=create(token,s,"EXPENSE","1","2026-01-01"),other=create(token,s,"INCOME","1","2026-01-01");for(int i=0;i<10;i++)create(token,s,"INCOME","1","2026-01-01");
  mvc.perform(get("/api/v1/finance/bills").param("shopId",s).param("from","2026-01-01").param("to","2026-12-31").param("page","1").param("size","10").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalCount").value(12));
  byte[] png=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aE1cAAAAASUVORK5CYII=");
  String uploaded=mvc.perform(multipart("/api/v1/finance/bills/"+id+"/attachments").file(new MockMultipartFile("file","凭证.png","image/png",png)).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();String aid=json.readTree(uploaded).path("id").asText();
  mvc.perform(get("/api/v1/finance/bills/"+id+"/attachments/"+aid).header("Authorization",token)).andExpect(status().isOk()).andExpect(content().bytes(png));
  mvc.perform(get("/api/v1/finance/bills/"+other+"/attachments/"+aid).header("Authorization",token)).andExpect(status().isNotFound());
  mvc.perform(multipart("/api/v1/finance/bills/"+id+"/attachments").file(new MockMultipartFile("file","fake.png","image/png","html".getBytes())).header("Authorization",token)).andExpect(status().isBadRequest());
  String csv=mvc.perform(get("/api/v1/finance/export").param("shopId",s).param("from","2026-01-01").param("to","2026-12-31").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
  assertThat(csv).contains("金额(CNY)").contains("合成明细");assertThat(csv.lines().count()).isEqualTo(13);
 }
 @Test void managesAccountsAndPreservesHistoricalBills()throws Exception{
  String token=user("ADMIN"),s=shop(token),other=shop(token),denied=user("PRODUCT_MATERIAL"),id=create(token,s,"INCOME","10","2026-01-01");
  String accountId=jdbc.queryForObject("SELECT id FROM finance_account WHERE shop_id=?",String.class,s);
  String path="/api/v1/finance/shops/"+s+"/accounts";
  mvc.perform(post(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"合成账户\",\"active\":true}")).andExpect(status().isConflict());
  mvc.perform(get(path).header("Authorization",denied)).andExpect(status().isForbidden());
  var update=Map.of("name","更名账户","active",false,"version",0);
  mvc.perform(put(path+"/"+accountId).header("Authorization",denied).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/finance/shops/"+other+"/accounts/"+accountId).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isNotFound());
  mvc.perform(put(path+"/"+accountId).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isOk());
  mvc.perform(put(path+"/"+accountId).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isConflict());
  var in=input(s,"INCOME","20","2026-01-01");in.remove("category");in.put("version",0);
  mvc.perform(put("/api/v1/finance/bills/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isOk()).andExpect(jsonPath("$.account").value("合成账户"));
  in.put("account","更名账户");mvc.perform(post("/api/v1/finance/bills").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isBadRequest());
  in.put("shopId",other);in.put("account","合成账户");mvc.perform(post("/api/v1/finance/bills").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isOk()).andExpect(jsonPath("$.category").value(""));
 }
 @Test void createsShopAndAccountsAtomically()throws Exception{
  String token=user("ADMIN"),name="合成批量店铺"+UUID.randomUUID();
  var in=Map.of("name",name,"active",true,"accounts",List.of("合成甲","合成乙"));
  String id=json.readTree(mvc.perform(post("/api/v1/finance/shops").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
  mvc.perform(get("/api/v1/finance/shops/"+id+"/accounts").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
  String duplicate="合成重复店铺"+UUID.randomUUID();
  mvc.perform(post("/api/v1/finance/shops").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name",duplicate,"active",true,"accounts",List.of("合成甲"," 合成甲 "))))).andExpect(status().isBadRequest());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_shop WHERE name=?",Integer.class,duplicate)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_account",Integer.class)).isEqualTo(2);
 }
 private Map<String,Object> input(String s,String d,String a,String date){return new HashMap<>(Map.of("shopId",s,"date",date,"direction",d,"category","测试分类","detail","合成明细","account","合成账户","amount",a,"remark",""));}
 private String create(String token,String s,String d,String a,String date)throws Exception{return json.readTree(mvc.perform(post("/api/v1/finance/bills").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input(s,d,a,date)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();}
 private String shop(String token)throws Exception{String id=json.readTree(mvc.perform(post("/api/v1/finance/shops").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name","合成店铺"+UUID.randomUUID(),"active",true)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();mvc.perform(post("/api/v1/finance/shops/"+id+"/accounts").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name","合成账户","active",true)))).andExpect(status().isOk());return id;}
 private String user(String role)throws Exception{String id=UUID.randomUUID().toString(),password="Synthetic-Finance-42!";jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic finance QA",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();}
}
