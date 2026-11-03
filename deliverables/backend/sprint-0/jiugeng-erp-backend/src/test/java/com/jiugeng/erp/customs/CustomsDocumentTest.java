package com.jiugeng.erp.customs;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.CellType;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class CustomsDocumentTest {
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired CustomsWorkbook exporter;
 @AfterEach void clean(){jdbc.update("DELETE FROM customs_document");}
 static Map<String,String> fields(){var f=new HashMap<String,String>();for(String k:CustomsDocuments.HEADERS)f.put(k,"");f.putAll(Map.of("contractNo","SYN-CONTRACT","invoiceNo","SYN-INVOICE","date","2026-11-03","seller","Synthetic Seller","sellerAddress","Synthetic seller address","buyer","Synthetic Buyer","buyerAddress","Synthetic buyer address","producer","Synthetic producer","destination","United States","currency","USD"));f.put("terms","FOB");f.put("packaging","Cartons");f.put("signPlace","Synthetic City");return f;}
 static Map<String,String> item(String sku){var r=new HashMap<String,String>();for(String k:CustomsDocuments.ITEM_FIELDS)r.put(k,"");r.putAll(Map.of("sku",sku,"name","Synthetic item","hsCode","1234567890","unit","PCS","quantity","3","price","1.235","cartons","2","grossWeight","5","netWeight","4","origin","China"));r.put("declarationElements","品牌类型：合成测试\n规格：合成规格");r.put("quantity2","4");r.put("unit2","kg");return r;}
 CustomsDocuments.Input input(int n){return new CustomsDocuments.Input("SYN-"+UUID.randomUUID(),"DRAFT",fields(),java.util.stream.IntStream.range(0,n).mapToObj(i->item("SYN-"+i)).toList(),null);}
 String create(String token,CustomsDocuments.Input in)throws Exception{return json.readTree(mvc.perform(post("/api/v1/customs-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();}
 @Test void exportsFiveSheetsWithGrowingRowsExactTotalsAndCorrectInvoiceDate()throws Exception{
  String token=user("ADMIN");for(int count:new int[]{1,3,12}){var in=input(count);String id=create(token,in);byte[] bytes=mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",token)).andExpect(status().isOk()).andExpect(header().string("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse().getContentAsByteArray();
   try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(w.getNumberOfSheets()).isEqualTo(5);var c=w.getSheet("报关单");var v=w.getSheet("发票");var p=w.getSheet("装箱单");var t=w.getSheet("合同");assertThat(v.getRow(8).getCell(6).getStringCellValue()).isEqualTo("2026-11-03");assertThat(v.getRow(5).getCell(6).getStringCellValue()).isEqualTo("SYN-INVOICE");assertThat(t.getRow(2).getCell(11).getStringCellValue()).isEqualTo("SYN-CONTRACT");assertThat(c.getRow(11).getCell(4).getNumericCellValue()).isEqualTo(count*2);assertThat(v.getRow(14+count).getCell(6).getNumericCellValue()).isCloseTo(count*3.71,within(.00001));assertThat(t.getRow(21+count).getCell(10).getNumericCellValue()).isCloseTo(count*3.71,within(.00001));assertThat(p.getRow(16+count).getCell(7).getNumericCellValue()).isEqualTo(count*5);assertThat(v.getRow(13+count).getCell(2).getStringCellValue()).isEqualTo("SYN-"+(count-1));for(var sheet:w){sheet.validateMergedRegions();for(var row:sheet)for(var cell:row)assertThat(cell.getCellType()==CellType.ERROR || cell.getCellType()==CellType.FORMULA&&cell.getCachedFormulaResultType()==CellType.ERROR).isFalse();}}
   if(count==3)java.nio.file.Files.write(java.nio.file.Path.of("/tmp/customs-redesign/export-test.xlsx"),bytes);
  }
 }
 @Test void savesDraftsChecksExportAndRejectsInvalidTotals()throws Exception{
  String token=user("ADMIN");var draft=new CustomsDocuments.Input("SYN-DRAFT","DRAFT",Map.of(),List.of(),null);String id=create(token,draft);
  mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",token)).andExpect(status().isBadRequest());
  var in=input(1);in.items().get(0).put("netWeight","6");mvc.perform(post("/api/v1/customs-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/customs-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new CustomsDocuments.Input("SYN-READY","READY",Map.of(),List.of(),null)))).andExpect(status().isBadRequest());
 }
 @Test void enforcesPermissionsVersionUniquenessAndVoid()throws Exception{
  String admin=user("ADMIN"),readOnly=user("SELECTION"),noAccess=user("NONE");var in=input(1);String id=create(admin,in);
  mvc.perform(post("/api/v1/customs-documents").header("Authorization",readOnly).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/customs-documents")).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",noAccess)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customs-documents").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isConflict());
  var edited=new CustomsDocuments.Input(in.documentNo(),"READY",in.fields(),in.items(),0L);
  mvc.perform(put("/api/v1/customs-documents/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edited))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(put("/api/v1/customs-documents/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edited))).andExpect(status().isConflict());
  mvc.perform(post("/api/v1/customs-documents/"+id+"/void?version=1").header("Authorization",admin)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",admin)).andExpect(status().isBadRequest());
  mvc.perform(get("/api/v1/customs-documents?status=VOID").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customs_document",Integer.class)).isEqualTo(1);
 }
 @Test void mixedUnitsAreNotAddedAndTextIsNeverAnExcelFormula()throws Exception{
  var rows=List.of(item("=1+1"),item("SYN-2"));rows.get(1).put("unit","SETS");byte[] bytes=exporter.export(new CustomsDocuments.Document("id","SYN","DRAFT",fields(),rows,0,""));try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(w.getSheet("发票").getRow(14).getCell(2).getCellType()).isEqualTo(CellType.STRING);assertThat(w.getSheet("发票").getRow(16).getCell(3).getStringCellValue()).isEqualTo("见明细");}
 }
 private String user(String role)throws Exception{String id=UUID.randomUUID().toString(),password="Synthetic-Customs-42!";jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic customs QA",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role.equals("NONE")?"SELECTION":role);if(!role.equals("NONE"))jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();}
}
