package com.jiugeng.erp.factory;
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
class FactoryControllerTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json; String token;
 @BeforeEach void before()throws Exception{token=user("ADMIN");}
 @AfterEach void cleanup(){jdbc.update("DELETE FROM factory_attachment");jdbc.update("DELETE FROM factory_request");jdbc.update("DELETE FROM factory_event");jdbc.update("DELETE FROM factory_record");}
 Map<String,Object> map(Object...args){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<args.length;i+=2)r.put(args[i].toString(),args[i+1]);return r;}
 JsonNode command(String kind,String action,Map<String,Object>payload,int expected)throws Exception{payload.putIfAbsent("requestId",UUID.randomUUID().toString());String result=mvc.perform(post("/api/v1/factory/"+kind+"/actions/"+action).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload))).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();return result.isEmpty()?json.createObjectNode():json.readTree(result);}
 JsonNode emp()throws Exception{return command("employees","save",map("name","Synthetic Factory Worker","date","2025-01-01","team","Synthetic"),200);}
 JsonNode rate(String date,String price,String employee)throws Exception{return command("rates","save",map("project","Synthetic Piece","operation","Synthetic Sewing","unit","件","date",date,"price",price,"employeeId",employee),200);}
 JsonNode report(String e,String r,String qty,String status)throws Exception{return command("reports","save",map("employeeId",e,"date","2026-01-10","status",status,"lines",List.of(map("rateId",r,"quantity",qty))),200);}
 Map<String,Object> identity(JsonNode r){return map("id",r.path("id").asText(),"version",r.path("version").asInt());}
 JsonNode getRecord(String k,String id)throws Exception{return json.readTree(mvc.perform(get("/api/v1/factory/"+k+"/"+id).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 @Test void payrollUsesCurrentEmployeeBankDetailsAndPreservesCardDigits()throws Exception{
   var employee=command("employees","save",map("name","Synthetic Bank Worker","date","2025-01-01","bankName","Synthetic Bank","bankAccount","0012345678901234567"),200);
   var payroll=command("payroll","generate",map("employeeId",employee.path("id").asText(),"date","2026-01"),200);
   assertThat(payroll.path("bankAccount").asText()).isEqualTo("0012345678901234567");
   var update=identity(employee);update.putAll(map("name","Synthetic Bank Worker","date","2025-01-01","bankName","Synthetic Branch","bankAccount","0099 887766554433221"));
   command("employees","save",update,200);
   var current=getRecord("payroll",payroll.path("id").asText());assertThat(current.path("bankName").asText()).isEqualTo("Synthetic Branch");assertThat(current.path("bankAccount").asText()).isEqualTo("0099887766554433221");
   var export=mvc.perform(get("/api/v1/factory/payroll-export").param("month","2026-01").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
   assertThat(export).contains("开户行,银行卡号").contains("\t0099887766554433221");
   command("employees","save",map("name","Synthetic Invalid Worker","date","2025-01-01","bankAccount","invalid-card"),400);
 }
 @Test void matchesHistoricalAndPersonalPricesPreservesSnapshotsAndIdempotency()throws Exception{
   String e=emp().path("id").asText();JsonNode old=rate("2026-01-01","0.4",""),later=rate("2026-01-15","0.7","");
   var input=map("employeeId",e,"date","2026-01-10","status","REVIEWED","lines",List.of(map("rateId",later.path("id").asText(),"quantity","120")));
   JsonNode r=command("reports","save",input,200);assertThat(r.path("amount").decimalValue()).isEqualByComparingTo("48.00");assertThat(command("reports","save",input,200).path("id")).isEqualTo(r.path("id"));
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factory_record WHERE kind='reports'",Integer.class)).isEqualTo(1);
   rate("2026-01-05","0.5",e);assertThat(getRecord("reports",r.path("id").asText()).path("amount").decimalValue()).isEqualByComparingTo("48.00");
   var preview=map("employeeId",e,"date","2026-01-10","rateId",old.path("id").asText());mvc.perform(post("/api/v1/factory/rate-preview").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(preview))).andExpect(status().isOk()).andExpect(jsonPath("$.price").value(0.5));
   rateDuplicate("2026-01-05",e);input.put("remark","changed");command("reports","save",input,400);
 }
 void rateDuplicate(String day,String e)throws Exception{command("rates","save",map("project","Synthetic Piece","operation","Synthetic Sewing","unit","件","date",day,"price","0.6","employeeId",e),400);}
 @Test void closesPayrollLoopAndKeepsPaymentsAcrossCorrection()throws Exception{
   String e=emp().path("id").asText(),r=rate("2026-01-01","0.4","").path("id").asText();JsonNode report=report(e,r,"120","REVIEWED");
   JsonNode p=command("payroll","generate",map("employeeId",e,"date","2026-01"),200);var adjust=identity(p);adjust.put("reason","Synthetic bonus");adjust.put("adjustments",List.of(map("direction","ADD","amount","20","remark","Synthetic subsidy")));p=command("payroll","adjust",adjust,200);
   var advance=identity(p);advance.putAll(map("amount","30","paymentDate","2026-01-10","method","现金","advance",true));p=command("payroll","pay",advance,200);assertThat(p.path("remaining").decimalValue()).isEqualByComparingTo("38");
   p=command("payroll","lock",identity(p),200);var withdrawal=identity(report);withdrawal.put("reason","Synthetic change");command("reports","withdraw",withdrawal,400);
   var pay=identity(p);pay.putAll(map("amount","38","paymentDate","2026-01-31","method","银行"));p=command("payroll","pay",pay,200);assertThat(p.path("remaining").decimalValue()).isEqualByComparingTo("0");
   var excess=identity(p);excess.putAll(map("amount","1","paymentDate","2026-01-31","method","银行"));command("payroll","pay",excess,400);
   var correction=identity(p);correction.put("reason","Synthetic correction");p=command("payroll","correct",correction,200);
   var lower=identity(p);lower.put("reason","Synthetic correction");lower.put("adjustments",List.of());p=command("payroll","adjust",lower,200);p=command("payroll","lock",identity(p),200);assertThat(p.path("remaining").decimalValue()).isEqualByComparingTo("-20");assertThat(p.path("revision").asInt()).isEqualTo(2);
   assertThat(p.path("payments").size()).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM factory_event WHERE record_id=?",Integer.class,p.path("id").asText())).isGreaterThan(6);
 }
 @Test void blocksStalePendingPayrollAndConcurrentVersions()throws Exception{
   String e=emp().path("id").asText(),rate=rate("2026-01-01","0.3","").path("id").asText();JsonNode r=report(e,rate,"10","DRAFT"),p=command("payroll","generate",map("employeeId",e,"date","2026-01"),200);
   command("payroll","lock",identity(p),400);command("reports","review",identity(r),200);command("reports","review",identity(r),409);command("payroll","lock",identity(p),400);p=command("payroll","calculate",identity(p),200);p=command("payroll","lock",identity(p),200);assertThat(p.path("amount").decimalValue()).isEqualByComparingTo("3");
 }
 @Test void validatesQuantityDuplicatesEmploymentAndBatchAtomicity()throws Exception{
   JsonNode employee=emp();String e=employee.path("id").asText(),rate=rate("2026-01-01","0.3","").path("id").asText();JsonNode r=report(e,rate,"10","DRAFT");
   var base=map("employeeId",e,"date","2026-01-10","status","DRAFT","lines",List.of(map("rateId",rate,"quantity","10")));command("reports","save",base,400);
   base.remove("requestId");base.put("duplicateConfirmed",true);base.put("remark","Synthetic second batch");command("reports","save",base,200);
   base.remove("requestId");base.put("lines",List.of(map("rateId",rate,"quantity","0.5")));command("reports","save",base,400);
   var end=identity(employee);end.putAll(map("name","Synthetic Factory Worker","date","2025-01-01","endDate","2026-01-09"));command("employees","save",end,400);
   command("reports","reviewBatch",map("items",List.of(identity(r),map("id","missing","version",0))),404);
   assertThat(getRecord("reports",r.path("id").asText()).path("status").asText()).isEqualTo("DRAFT");
 }
 @Test void historicalOpeningDoesNotDoubleCountReports()throws Exception{
   String e=emp().path("id").asText(),r=rate("2026-01-01","0.4","").path("id").asText();var opening=map("employeeId",e,"date","2026-01","historical",true,"historicalAmount","123.45","remark","Synthetic opening");JsonNode p=command("payroll","generate",opening,200);assertThat(p.path("amount").decimalValue()).isEqualByComparingTo("123.45");command("reports","save",map("employeeId",e,"date","2026-01-10","status","DRAFT","lines",List.of(map("rateId",r,"quantity","10"))),400);
 }
 @Test void guardsSalaryAndHistoryAndExport()throws Exception{
   String e=emp().path("id").asText();JsonNode p=command("payroll","generate",map("employeeId",e,"date","2026-01"),200);
   mvc.perform(get("/api/v1/factory/payroll")).andExpect(status().isUnauthorized());String reader=user("SELECTION");
   for(String path:List.of("/payroll","/payroll/"+p.path("id").asText(),"/payroll/"+p.path("id").asText()+"/history","/payroll-export?month=2026-01","/employees"))mvc.perform(get("/api/v1/factory"+path).header("Authorization",reader)).andExpect(status().isForbidden());
   mvc.perform(get("/api/v1/factory/payroll-export").param("month","2026-01").header("Authorization",token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
   mvc.perform(get("/api/v1/factory/employees").param("size","999").header("Authorization",token)).andExpect(status().isBadRequest());
   assertThat(FactoryController.cell("=SUM(A1)")).startsWith("\"'");
 }

 @Test void unpricedDraftRequiresResolutionBeforeReview()throws Exception{
   String e=emp().path("id").asText(),rate=rate("2026-02-01","0.4","").path("id").asText();JsonNode draft=report(e,rate,"10","DRAFT");assertThat(draft.path("pricingIncomplete").asBoolean()).isTrue();command("reports","review",identity(draft),400);
 }
 @Test void privateAttachmentsValidateFormatAndAccess()throws Exception{
   String e=emp().path("id").asText(),rate=rate("2026-01-01","0.4","").path("id").asText();JsonNode r=report(e,rate,"10","DRAFT");String path="/api/v1/factory/reports/"+r.path("id").asText()+"/attachments";
   var bad=new org.springframework.mock.web.MockMultipartFile("file","bad.png","image/png","not an image".getBytes());mvc.perform(multipart(path).file(bad).header("Authorization",token)).andExpect(status().isBadRequest());
   var file=new org.springframework.mock.web.MockMultipartFile("file","synthetic.pdf","application/pdf","%PDF-1.4 synthetic test".getBytes());var result=json.readTree(mvc.perform(multipart(path).file(file).header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
   mvc.perform(get(path+"/"+result.path("id").asText()).header("Authorization",token)).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"));
   mvc.perform(get(path+"/"+result.path("id").asText()).header("Authorization",user("SELECTION"))).andExpect(status().isForbidden());
 }
 @Test void archivesReferencedEmployeeWithoutDeletingReportsAndRoundsPerLine()throws Exception{
   var e=emp();String id=e.path("id").asText(),r=rate("2026-01-01","0.333333","").path("id").asText();var report=command("reports","save",map("employeeId",id,"date","2026-01-10","status","REVIEWED","duplicateConfirmed",true,"remark","Synthetic separate batches","lines",List.of(map("rateId",r,"quantity","1"),map("rateId",r,"quantity","1"))),200);assertThat(report.path("amount").decimalValue()).isEqualByComparingTo("0.66");var removal=identity(e);removal.put("reason","Synthetic removal");command("employees","remove",removal,200);assertThat(getRecord("reports",report.path("id").asText()).path("amount").decimalValue()).isEqualByComparingTo("0.66");
   command("reports","save",map("employeeId",id,"date","2026-01-11","status","DRAFT","lines",List.of(map("rateId",r,"quantity","1"))),400);
 }
 @Test void employeeNumbersIdentityAndInsurance()throws Exception{
  var first=emp();assertThat(first.path("number").asText()).isEqualTo("CS-0001");assertThat(first.path("insured").asBoolean()).isFalse();command("employees","remove",identity(first),200);
  var next=command("employees","save",map("name","Synthetic Identity Worker","date","2025-01-01","identityNumber","00000000000000000x","insured",true),200);assertThat(next.path("number").asText()).isEqualTo("CS-0002");assertThat(next.path("identityNumber").asText()).isEqualTo("00000000000000000X");assertThat(next.path("insured").asBoolean()).isTrue();
  command("employees","save",map("name","Invalid","date","2025-01-01","identityNumber","invalid"),400);command("employees","save",map("name","Invalid","date","2025-01-01","insured","yes"),400);
 }
 @Test void singleProjectContinuesLegacyRateHistoryAndExportsOneField()throws Exception{
  var e=emp();var old=rate("2026-01-01","0.4","");var newer=command("rates","save",map("project","Synthetic Piece / Synthetic Sewing","unit","件","date","2026-01-05","price","0.6"),200);
  var r=report(e.path("id").asText(),old.path("id").asText(),"10","REVIEWED");assertThat(r.path("amount").decimalValue()).isEqualByComparingTo("6");
  command("rates","save",map("project","Synthetic Piece / Synthetic Sewing","unit","件","date","2026-01-05","price","0.7"),400);
  command("payroll","generate",map("employeeId",e.path("id").asText(),"date","2026-01"),200);
  String csv=mvc.perform(get("/api/v1/factory/payroll-export").param("month","2026-01").param("mode","lines").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(csv).contains("加工项目,单位").doesNotContain("工序").contains("Synthetic Piece / Synthetic Sewing");
 }
 @Test void expenseLedgerValidationVersionTotalsPermissionsAndVouchers()throws Exception{
  var input=map("name","Synthetic Utility","date","2026-01-01","amount","12.50","payee","Synthetic payee");var e=command("expenses","save",input,200);assertThat(command("expenses","save",input,200).path("id")).isEqualTo(e.path("id"));
  mvc.perform(get("/api/v1/factory/expenses").param("q","Synthetic payee").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(12.5));
  var edit=identity(e);edit.putAll(map("name","Synthetic Utility","date","2026-01-02","amount","15"));var updated=command("expenses","save",edit,200);edit.put("requestId",UUID.randomUUID().toString());command("expenses","save",edit,409);
  command("expenses","save",map("name","Invalid","date","2026-01-01","amount","-1"),400);command("expenses","save",map("name","Invalid","date","2026-01-01","amount","0.001"),400);
  String path="/api/v1/factory/expenses/"+e.path("id").asText()+"/attachments";var file=new org.springframework.mock.web.MockMultipartFile("file","synthetic.pdf","application/pdf","%PDF-1.4 synthetic".getBytes());mvc.perform(multipart(path).file(file).header("Authorization",token)).andExpect(status().isOk());
  mvc.perform(get(path).header("Authorization",user("SELECTION"))).andExpect(status().isForbidden());mvc.perform(get("/api/v1/factory/expenses").header("Authorization",user("SELECTION"))).andExpect(status().isForbidden());
  command("expenses","remove",identity(updated),200);mvc.perform(get("/api/v1/factory/expenses").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0)).andExpect(jsonPath("$.amount").value(0));mvc.perform(multipart(path).file(file).header("Authorization",token)).andExpect(status().isBadRequest());
 }
 String user(String role)throws Exception{String id=UUID.randomUUID().toString(),password="Synthetic-Factory-Test-42!";jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic factory QA",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);jdbc.update("INSERT INTO iam_user_role(user_id,role_id) VALUES(?,?)",id,role);return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(map("loginName",id,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();}
}
