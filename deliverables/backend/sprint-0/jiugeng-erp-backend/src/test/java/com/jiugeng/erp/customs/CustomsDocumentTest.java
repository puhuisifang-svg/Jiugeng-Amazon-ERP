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
 @org.junit.jupiter.api.io.TempDir java.nio.file.Path exportDirectory;
 @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;@Autowired CustomsWorkbook exporter;
 @AfterEach void clean(){jdbc.update("DELETE FROM customs_document");}
 static Map<String,String> fields(){var f=new HashMap<String,String>();for(String k:CustomsDocuments.HEADERS)f.put(k,"");f.putAll(Map.of("contractNo","SYN-CONTRACT","invoiceNo","SYN-INVOICE","date","2026-11-03","seller","Synthetic Seller","sellerAddress","Synthetic seller address","buyer","Synthetic Buyer","buyerAddress","Synthetic buyer address","producer","Synthetic producer","destination","United States","currency","USD"));f.put("terms","FOB");f.put("packaging","Cartons");f.put("signPlace","Synthetic City");return f;}
 static Map<String,String> item(String sku){var r=new HashMap<String,String>();for(String k:CustomsDocuments.ITEM_FIELDS)r.put(k,"");r.putAll(Map.of("sku",sku,"name","Synthetic item","hsCode","1234567890","unit","PCS","quantity","3","price","1.235","cartons","2","grossWeight","5","netWeight","4","origin","China"));r.put("declarationElements","品牌类型：合成测试\n规格：合成规格");r.put("quantity2","4");r.put("unit2","kg");return r;}
 CustomsDocuments.Input input(int n){return new CustomsDocuments.Input("SYN-"+UUID.randomUUID(),"DRAFT",fields(),java.util.stream.IntStream.range(0,n).mapToObj(i->item("SYN-"+i)).toList(),null);}
 String create(String token,CustomsDocuments.Input in)throws Exception{return json.readTree(mvc.perform(post("/api/v1/customs-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();}
 @Test void exportsMissingDefaultsForOldAndNewDocumentsAcrossSkuCounts()throws Exception{
  for(int count:new int[]{1,2,3,12}){
   var in=input(count);for(var row:in.items()){row.put("quantity2","");row.put("unit2","");}
   try(var w=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(new CustomsDocuments.Document("id",in.documentNo(),"DRAFT",in.fields(),in.items(),0,""))))){
    var c=w.getSheet("报关单");var t=w.getSheet("合同");
    for(int i=0;i<count;i++){assertThat(c.getRow(18+3*i).getCell(6).getNumericCellValue()).isEqualTo(4);assertThat(c.getRow(18+3*i).getCell(6).getCellFormula()).isEqualTo("装箱单!I"+(17+i));assertThat(c.getRow(18+3*i).getCell(7).getStringCellValue()).isEqualTo("千克");}
    assertThat(t.getRow(30+count).getCell(0).getStringCellValue()).isEqualTo("（9）付款条件：T/T");assertThat(w.getSheet("装箱单").getRow(10).getCell(8).getStringCellValue()).isEqualTo("T/T");
    assertThat(t.getRow(20+count).getCell(2).getNumericCellValue()).isEqualTo(5);assertThat(t.getRow(21+count).getCell(0).getNumericCellValue()).isEqualTo(5);
   }
   assertThat(in.fields().get("tolerance")).isEmpty();assertThat(in.items().getFirst().get("quantity2")).isEmpty();
  }
  var in=input(1);in.fields().put("tolerance","0");in.fields().put("payment","30 days after invoice");in.items().getFirst().put("quantity2","7");
  try(var w=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(new CustomsDocuments.Document("id","SYN","DRAFT",in.fields(),in.items(),0,""))))){assertThat(w.getSheet("装箱单").getRow(10).getCell(8).getStringCellValue()).isEqualTo("30 days after invoice");assertThat(w.getSheet("合同").getRow(21).getCell(2).getNumericCellValue()).isZero();assertThat(w.getSheet("报关单").getRow(18).getCell(6).getNumericCellValue()).isEqualTo(7);assertThat(w.getSheet("报关单").getRow(18).getCell(7).getStringCellValue()).isEqualTo("kg");}
  String admin=user("ADMIN"),id=create(admin,input(1));mvc.perform(get("/api/v1/customs-documents/"+id).header("Authorization",admin)).andExpect(jsonPath("$.fields.tolerance").value("5")).andExpect(jsonPath("$.fields.payment").value("T/T"));
 }
 @Test void filesDetermineCompletionAndFilterAcrossPages()throws Exception{
  String admin=user("ADMIN"),none=user("NONE");var in=input(1);in.fields().put("fbaNo","SYN-FBA-1234");in.fields().put("sellerPrefix","SYN");String id=create(admin,in),base="/api/v1/customs-documents/"+id+"/attachments";
  mvc.perform(get("/api/v1/customs-documents").param("fba","fba-1234").param("status","PENDING_FILES").header("Authorization",admin)).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].fileStatus").value("PENDING_FILES"));
  mvc.perform(get("/api/v1/customs-documents").param("fba","no-match").header("Authorization",admin)).andExpect(jsonPath("$.totalCount").value(0));
  var image=new org.springframework.mock.web.MockMultipartFile("file","synthetic.jpg","image/jpeg",new byte[]{(byte)137,80,78,71,13,10,26,10,0});
  var pdf=new org.springframework.mock.web.MockMultipartFile("file","synthetic.pdf","application/pdf","%PDF-synthetic".getBytes());
  mvc.perform(multipart(base).file(image).param("category","SCREENSHOT").header("Authorization",none)).andExpect(status().isForbidden());
  mvc.perform(multipart(base).file(pdf).param("category","SCREENSHOT").header("Authorization",admin)).andExpect(status().isBadRequest());
  mvc.perform(multipart(base).file(image).param("category","BAD").header("Authorization",admin)).andExpect(status().isBadRequest());
  mvc.perform(multipart(base).file(new org.springframework.mock.web.MockMultipartFile("file","bad.png","image/png","bad".getBytes())).param("category","SCREENSHOT").header("Authorization",admin)).andExpect(status().isBadRequest());
  String screenshot=json.readTree(mvc.perform(multipart(base).file(image).param("category","SCREENSHOT").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("synthetic.png")).andExpect(jsonPath("$.mediaType").value("image/png")).andReturn().getResponse().getContentAsString()).path("id").asText();
  mvc.perform(multipart(base).file(pdf).param("category","OTHER").header("Authorization",admin)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/customs-documents").param("status","COMPLETED").header("Authorization",admin)).andExpect(jsonPath("$.totalCount").value(0));
  mvc.perform(multipart(base).file(pdf).param("category","INVOICE").header("Authorization",admin)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/customs-documents").param("status","COMPLETED").header("Authorization",admin)).andExpect(jsonPath("$.totalCount").value(1));
  mvc.perform(get("/api/v1/customs-documents/"+id).header("Authorization",admin)).andExpect(jsonPath("$.fileStatus").value("COMPLETED"));
  mvc.perform(put("/api/v1/customs-documents/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new CustomsDocuments.Input(in.documentNo(),in.status(),in.fields(),in.items(),3L)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("已完成单据仅可查看，不允许编辑"));
  mvc.perform(get(base+"/"+screenshot).header("Authorization",none)).andExpect(status().isForbidden());
  mvc.perform(delete(base+"/"+screenshot).header("Authorization",none)).andExpect(status().isForbidden());
  String other=create(admin,input(1));mvc.perform(get("/api/v1/customs-documents/"+other+"/attachments/"+screenshot).header("Authorization",admin)).andExpect(status().isNotFound());
  mvc.perform(delete(base+"/"+screenshot).header("Authorization",admin)).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/customs-documents").param("fba","1234").param("status","PENDING_FILES").header("Authorization",admin)).andExpect(jsonPath("$.totalCount").value(1));
  mvc.perform(post("/api/v1/customs-documents/"+id+"/void").param("version","4").header("Authorization",admin)).andExpect(status().isOk());
  mvc.perform(multipart(base).file(pdf).param("category","INVOICE").header("Authorization",admin)).andExpect(status().isBadRequest());
  assertThat(CustomsDocuments.exportName(new CustomsDocuments.Document(id,in.documentNo(),"DRAFT",in.fields(),in.items(),0,""))).isEqualTo("SYN报关单资料-11-3-SYN-FBA-1234.xlsx");
 }
 @Test void totalWordsSuffixAndAllDatesStayConsistentAfterRecalculation()throws Exception{
  assertThat(CustomsWorkbook.amountWords(new java.math.BigDecimal("100010001"))).isEqualTo("壹亿零壹万零壹元整");
  assertThat(CustomsWorkbook.amountWords(new java.math.BigDecimal("4800.30"))).isEqualTo("肆仟捌佰点叁元");
  var in=input(2);in.items().forEach(r->{r.put("quantity","300");r.put("price","8");});
  try(var w=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(new CustomsDocuments.Document("synthetic",in.documentNo(),"DRAFT",in.fields(),in.items(),0,""))))){
   var total=w.getSheet("合同").getRow(24).getCell(3);
   assertThat(total.getStringCellValue()).isEqualTo("肆仟捌佰元整");assertThat(total.getCellFormula()).contains("K24");
   for(var pair:new String[][]{{"合同","L5"},{"发票","G9"},{"装箱单","I6"}}){var ref=new org.apache.poi.ss.util.CellReference(pair[1]);assertThat(w.getSheet(pair[0]).getRow(ref.getRow()).getCell(ref.getCol()).getCellStyle().getDataFormatString()).isEqualTo("yyyy/m/d");}
   w.getSheet("报关单").getRow(17).getCell(8).setCellValue(8.001);
   var evaluator=w.getCreationHelper().createFormulaEvaluator();evaluator.clearAllCachedResultValues();evaluator.evaluateAll();
   assertThat(total.getStringCellValue()).endsWith("元").doesNotEndWith("元整");
  }
 }
 @Test void exportLinksSheetsAndNumbersPackingRowsForMultipleSkus()throws Exception{
  for(int n:new int[]{1,2,3,12}){
   var in=input(n);in.fields().put("warehouse","SYN1");in.fields().put("payment","T/T");in.items().forEach(r->r.put("sourcePlace","厦门市"));
   try(var w=new XSSFWorkbook(new ByteArrayInputStream(exporter.export(new CustomsDocuments.Document("synthetic",in.documentNo(),"DRAFT",in.fields(),in.items(),0,""))))){
    var c=w.getSheet("报关单");var t=w.getSheet("合同");var v=w.getSheet("发票");var p=w.getSheet("装箱单");
    assertThat(t.getRow(6).getCell(11).getCellFormula()).isEqualTo("报关单!O18");assertThat(t.getRow(6).getCell(11).getStringCellValue()).isEqualTo("厦门市");
    assertThat(p.getRow(10).getCell(8).getStringCellValue()).isEqualTo("T/T");
    for(int i=0;i<n;i++)assertThat(p.getRow(16+i).getCell(0).getNumericCellValue()).isEqualTo(i+1);
    assertThat(v.getRow(8).getCell(1).getStringCellValue()).startsWith("地址1：SYN1 - ");
    c.getRow(17).getCell(6).setCellValue(10);c.getRow(17).getCell(8).setCellValue(2);c.getRow(17).getCell(14).setCellValue("泉州市");p.getRow(16).getCell(7).setCellValue(12);
    var evaluator=w.getCreationHelper().createFormulaEvaluator();evaluator.clearAllCachedResultValues();evaluator.evaluateAll();
    assertThat(t.getRow(19).getCell(5).getNumericCellValue()).isEqualTo(10);assertThat(v.getRow(14).getCell(6).getNumericCellValue()).isEqualTo(20);assertThat(p.getRow(16).getCell(5).getNumericCellValue()).isEqualTo(10);assertThat(c.getRow(11).getCell(5).getNumericCellValue()).isEqualTo(12+5*(n-1));assertThat(t.getRow(6).getCell(11).getStringCellValue()).isEqualTo("泉州市");
   }
  }
  assertThat(CustomsWorkbook.shippingAddress(Map.of("warehouse","SYN1","buyerAddress","地址1：SYN1 - Existing"))).isEqualTo("地址1：SYN1 - Existing");
 }
 @Test void warehousePermissionsVersionsBatchRollbackAndDocumentSnapshots()throws Exception{
  String admin=user("ADMIN"),none=user("NONE");String code="SYN"+UUID.randomUUID().toString().substring(0,8).toUpperCase();
  var row=new CustomsWarehouses.Warehouse(code,"US","CA","Synthetic City","01234","Synthetic full address","Synthetic verified source","2026-01-01",null);
  String body=json.writeValueAsString(row);
  mvc.perform(get("/api/v1/customs-warehouses/sync").header("Authorization",none)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customs-warehouses/sync").header("Authorization",none)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/customs-warehouses").header("Authorization",none)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customs-warehouses").header("Authorization",none).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customs-warehouses").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
  mvc.perform(post("/api/v1/customs-warehouses").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
  var in=input(1);in.fields().put("warehouse",code);in.fields().put("buyerAddress",row.address());String id=create(admin,in);
  var updated=new CustomsWarehouses.Warehouse(code,"US","CA","Synthetic City","01234","New synthetic address",row.source(),row.verifiedOn(),0L);
  mvc.perform(put("/api/v1/customs-warehouses/"+code).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(updated))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
  mvc.perform(put("/api/v1/customs-warehouses/"+code).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(updated))).andExpect(status().isConflict());
  mvc.perform(get("/api/v1/customs-documents/"+id).header("Authorization",admin)).andExpect(jsonPath("$.fields.buyerAddress").value(row.address()));
  var another=new CustomsWarehouses.Warehouse(code+"A","US","","","",row.address(),row.source(),"",null);
  mvc.perform(post("/api/v1/customs-warehouses/import").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(List.of(another,row)))).andExpect(status().isConflict());
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customs_warehouse WHERE code=?",Integer.class,code+"A")).isZero();
  mvc.perform(post("/api/v1/customs-warehouses/import").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(List.of(another)))).andExpect(status().isOk());
  mvc.perform(post("/api/v1/customs-warehouses").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"!!\",\"country\":\"US\"}")).andExpect(status().isBadRequest());
  jdbc.update("DELETE FROM customs_warehouse WHERE code IN (?,?)",code,code+"A");
 }
 @Test void deletedNumbersCanBeReusedWhileLiveDuplicatesRemainBlocked()throws Exception{
  String admin=user("ADMIN");var in=input(1);String first=create(admin,in);
  mvc.perform(delete("/api/v1/customs-documents/"+first).param("version","0").header("Authorization",admin)).andExpect(status().isNoContent());
  String second=create(admin,in);assertThat(second).isNotEqualTo(first);
  mvc.perform(post("/api/v1/customs-documents").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isConflict());
  mvc.perform(delete("/api/v1/customs-documents/"+second).param("version","0").header("Authorization",admin)).andExpect(status().isNoContent());
  String third=create(admin,in);assertThat(third).isNotIn(first,second);
  mvc.perform(get("/api/v1/customs-documents").param("q",in.documentNo()).header("Authorization",admin)).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].id").value(third));
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customs_document WHERE document_no=?",Integer.class,in.documentNo())).isEqualTo(3);
  String another=create(admin,input(1));var renamed=new CustomsDocuments.Input(in.documentNo(),"DRAFT",in.fields(),in.items(),0L);
  mvc.perform(put("/api/v1/customs-documents/"+another).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(renamed))).andExpect(status().isConflict());
 }
 @Test void deletesAllDocumentStatesWithPermissionAndVersionChecksAndKeepsAudit()throws Exception{
  String admin=user("ADMIN"),reader=user("SELECTION");
  for(String state:List.of("DRAFT","READY","VOID")){
   var original=input(1);var in=new CustomsDocuments.Input(original.documentNo(),state.equals("READY")?"READY":"DRAFT",original.fields(),original.items(),null);String id=create(admin,in);long version=0;
   if(state.equals("VOID")){mvc.perform(post("/api/v1/customs-documents/"+id+"/void").param("version","0").header("Authorization",admin)).andExpect(status().isOk());version=1;}
   mvc.perform(delete("/api/v1/customs-documents/"+id).param("version",Long.toString(version)).header("Authorization",reader)).andExpect(status().isForbidden());
   mvc.perform(delete("/api/v1/customs-documents/"+id).param("version","99").header("Authorization",admin)).andExpect(status().isConflict());
   mvc.perform(delete("/api/v1/customs-documents/"+id).param("version",Long.toString(version)).header("Authorization",admin)).andExpect(status().isNoContent());
   mvc.perform(get("/api/v1/customs-documents").param("q",in.documentNo()).header("Authorization",admin)).andExpect(jsonPath("$.totalCount").value(0));
   mvc.perform(get("/api/v1/customs-documents/"+id).header("Authorization",admin)).andExpect(status().isNotFound());
   mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",admin)).andExpect(status().isNotFound());
   mvc.perform(put("/api/v1/customs-documents/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isNotFound());
   mvc.perform(post("/api/v1/customs-documents/"+id+"/void").param("version",Long.toString(version+1)).header("Authorization",admin)).andExpect(status().isConflict());
   mvc.perform(delete("/api/v1/customs-documents/"+id).param("version",Long.toString(version)).header("Authorization",admin)).andExpect(status().isNotFound());
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customs_document WHERE id=? AND deleted_at IS NOT NULL",Integer.class,id)).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='CUSTOMS_DOCUMENT_DELETE'",Integer.class,id)).isEqualTo(1);
  }
 }
 @Test void summaryIncludesShipmentFieldsAndSumsCartonsAcrossSkus()throws Exception{
  String token=user("ADMIN");var f=fields();f.putAll(Map.of("fbaNo","FBA-SYN-1234","warehouse","SYN1","freight","USD/400"));
  create(token,new CustomsDocuments.Input("SYN-LIST-COLUMNS","DRAFT",f,List.of(item("SYN-A"),item("SYN-B")),null));
  mvc.perform(get("/api/v1/customs-documents").param("q","SYN-LIST-COLUMNS").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].fbaNo").value("FBA-SYN-1234")).andExpect(jsonPath("$.items[0].warehouse").value("SYN1")).andExpect(jsonPath("$.items[0].cartonCount").value("4")).andExpect(jsonPath("$.items[0].freight").value("USD/400"));
  assertThat(CustomsDocuments.cartonCount(List.of(Map.of("cartons","2"),Map.of("cartons","")))).isNull();
 }
 @Test void invoiceUsesDocumentNumberAndDefaultsAreAppliedWithoutReplacingExplicitTerms()throws Exception{
  String token=user("ADMIN");var f=fields();f.put("currency","CNY");f.put("invoiceNo","wrong");f.put("terms","");f.put("packaging","");f.put("destination","");
  String id=create(token,new CustomsDocuments.Input("SYN-NUMBER-123","DRAFT",f,List.of(),null));
  mvc.perform(get("/api/v1/customs-documents/"+id).header("Authorization",token)).andExpect(jsonPath("$.fields.invoiceNo").value("SYN-NUMBER-123")).andExpect(jsonPath("$.fields.currency").value("USD")).andExpect(jsonPath("$.fields.terms").value("c&f")).andExpect(jsonPath("$.fields.packaging").value("纸制或纤维板制盒/箱")).andExpect(jsonPath("$.fields.destination").value("美国")).andExpect(jsonPath("$.fields.transport").value("水路运输")).andExpect(jsonPath("$.fields.supervision").value("跨境电商出口海外仓")).andExpect(jsonPath("$.fields.taxNature").value("一般征税")).andExpect(jsonPath("$.fields.tradeCountry").value("美国")).andExpect(jsonPath("$.fields.documents").value(org.hamcrest.Matchers.containsString("随附单证3：合同；发票；装箱单")));
 }
 @Test void dynamicSchemaSnapshotSurvivesSaveAndExport()throws Exception{
  String token=user("ADMIN");var row=item("SYN-HS");row.putAll(Map.of("elementsVersion","2","hsCode","6307909000","schemaCode","6307909000","measurementUnit","千克/个","elementFields","[{\"key\":\"compositionContent\",\"label\":\"成分含量\"},{\"key\":\"articleNumber\",\"label\":\"货号\"}]","compositionContent","合成成分","articleNumber","TEST-01"));
  var in=new CustomsDocuments.Input("SYN-DYNAMIC","DRAFT",fields(),List.of(row),null);String id=create(token,in);
  mvc.perform(get("/api/v1/customs-documents/"+id).header("Authorization",token)).andExpect(jsonPath("$.items[0].declarationElements").value("成分含量：合成成分\n货号：TEST-01"));
  byte[] bytes=mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))){var sheet=w.getSheet("申报要素");assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("成分含量");assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("TEST-01");assertThat(sheet.getLastRowNum()).isEqualTo(4);}
  row.put("hsCode","5705002000");mvc.perform(post("/api/v1/customs-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isBadRequest());
 }
 @Test void newRulesGenerateNumbersAndExportSummedShipmentWeights()throws Exception{
  String token=user("ADMIN");var f=fields();f.putAll(Map.of("ruleVersion","2","sellerPrefix","xmh","fbaNo","STAR-TEST0123","warehouse","SYN1","grossWeight","99.5","netWeight","88.5","producer","wrong","orderNo","wrong","buyer","wrong"));
  var in=new CustomsDocuments.Input("","DRAFT",f,List.of(item("SYN-A"),item("SYN-B")),null);String id=create(token,in);
  mvc.perform(get("/api/v1/customs-documents/"+id).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.fields.contractNo").value("XMH202611030123")).andExpect(jsonPath("$.fields.orderNo").value("XMH202611030123")).andExpect(jsonPath("$.fields.producer").value("Synthetic Seller")).andExpect(jsonPath("$.fields.buyer").value("Amazon.com Services.Inc"));
  byte[] bytes=mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))){var c=w.getSheet("报关单");assertThat(c.getRow(11).getCell(4).getNumericCellValue()).isEqualTo(4);assertThat(c.getRow(11).getCell(5).getNumericCellValue()).isEqualTo(10);assertThat(c.getRow(11).getCell(6).getNumericCellValue()).isEqualTo(8);assertThat(w.getSheet("装箱单").getRow(18).getCell(7).getNumericCellValue()).isEqualTo(10);}
  f.put("grossWeight","1");mvc.perform(post("/api/v1/customs-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isBadRequest());
 }
 @Test void shipperDirectoryPersistsAndRejectsDuplicatesAndUnauthorizedWrites()throws Exception{
  String admin=user("ADMIN"),reader=user("SELECTION");String name="Synthetic "+UUID.randomUUID();String body=json.writeValueAsString(Map.of("name",name,"prefix","syn","address","Synthetic address","phone","","fax",""));
  mvc.perform(post("/api/v1/customs-shippers").header("Authorization",reader).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/customs-shippers").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.prefix").value("SYN"));
  mvc.perform(post("/api/v1/customs-shippers").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
  mvc.perform(get("/api/v1/customs-shippers").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.name=='"+name+"')].address").value("Synthetic address"));
 }
 @Test void editsShipperDirectoryWithoutChangingSavedDocumentSnapshots()throws Exception{
  String admin=user("ADMIN"),reader=user("SELECTION"),name="Synthetic "+UUID.randomUUID();
  String body=json.writeValueAsString(Map.of("name",name,"prefix","syn","address","Synthetic address"));
  String id=json.readTree(mvc.perform(post("/api/v1/customs-shippers").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getContentAsString()).path("id").asText();
  var f=fields();f.put("seller",name);f.put("shipperId",id);String doc=create(admin,new CustomsDocuments.Input("SYN-SNAPSHOT","DRAFT",f,List.of(),null));
  String edited=json.writeValueAsString(Map.of("name",name+" Edited","prefix","new","address","Updated address"));
  mvc.perform(put("/api/v1/customs-shippers/"+id).header("Authorization",reader).contentType(MediaType.APPLICATION_JSON).content(edited)).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/customs-shippers/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(edited)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name+" Edited")).andExpect(jsonPath("$.prefix").value("NEW"));
  mvc.perform(get("/api/v1/customs-documents/"+doc).header("Authorization",admin)).andExpect(jsonPath("$.fields.seller").value(name));
  mvc.perform(put("/api/v1/customs-shippers/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"prefix\":\"bad!\"}")).andExpect(status().isBadRequest());
  mvc.perform(put("/api/v1/customs-shippers/missing").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(edited)).andExpect(status().isNotFound());
 }
 @Test void preservesTemplateLogoLayoutAndPerSkuElementBlocks()throws Exception{
  String token=user("ADMIN");var first=item("SYN-BLACK");var second=item("SYN-GRAY");
  var schema=List.of(new com.jiugeng.erp.hs.DeclarationSchema.Field("brandType","品牌类型"),new com.jiugeng.erp.hs.DeclarationSchema.Field("exportBenefit","出口享惠情况"),new com.jiugeng.erp.hs.DeclarationSchema.Field("compositionContent","成分含量"),new com.jiugeng.erp.hs.DeclarationSchema.Field("brand","品牌"),new com.jiugeng.erp.hs.DeclarationSchema.Field("articleNumber","货号"),new com.jiugeng.erp.hs.DeclarationSchema.Field("gtin","GTIN"),new com.jiugeng.erp.hs.DeclarationSchema.Field("cas","CAS"),new com.jiugeng.erp.hs.DeclarationSchema.Field("other","其他"));
  for(var row:List.of(first,second)){row.putAll(Map.of("elementsVersion","2","schemaCode","6307909000","hsCode","6307909000","elementFields",json.writeValueAsString(schema),"brandType","无品牌","exportBenefit","目的国不享惠","compositionContent","80%无纺布，20%PVC","brand","无品牌","articleNumber",row.get("sku")));}
  assertThat(CustomsWorkbook.specification(second)).isEqualTo("无品牌|目的国不享惠|80%无纺布,20%PVC|无品牌|SYN-GRAY|0|0|0");
  var f=fields();f.put("seller","合成测试贸易有限公司");f.put("documents","随附单证1：跨境B2B出口单证\n随附单证2：代理报关委托协议（电子）\n随附单证3：合同；发票；装箱单");
  String id=create(token,new CustomsDocuments.Input("SYN-TEMPLATE","DRAFT",f,List.of(first,second),null));
  byte[] bytes=mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
  java.nio.file.Files.write(exportDirectory.resolve("export-template-two.xlsx"),bytes);
  try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes));var template=new XSSFWorkbook(getClass().getResourceAsStream("/templates/customs-document.xlsx"))){var c=w.getSheet("报关单");var e=w.getSheet("申报要素");
   assertThat(c.getDrawingPatriarch().getShapes()).hasSize(1);assertThat(c.getDrawingPatriarch().getShapes().getFirst()).isInstanceOf(org.apache.poi.xssf.usermodel.XSSFPicture.class);
   assertThat(((org.apache.poi.xssf.usermodel.XSSFPicture)c.getDrawingPatriarch().getShapes().getFirst()).getPictureData().getData()).isEqualTo(((org.apache.poi.xssf.usermodel.XSSFPicture)template.getSheet("报关单").getDrawingPatriarch().getShapes().getFirst()).getPictureData().getData());
   assertThat(c.getMergedRegions()).isEqualTo(template.getSheet("报关单").getMergedRegions());
   assertThat(e.getDrawingPatriarch().getShapes()).hasSize(1);
   for(int r=1;r<=10;r++)for(int col=0;col<2;col++)assertThat(e.getRow(r).getCell(col).getCellStyle().getIndex()).isEqualTo(template.getSheet("申报要素").getRow(r).getCell(col).getCellStyle().getIndex());
   assertThat(c.getRow(3).getCell(0).getCellStyle().getWrapText()).isFalse();assertThat(c.getRow(3).getHeight()).isEqualTo(template.getSheet("报关单").getRow(3).getHeight());
   assertThat(c.getRow(3).getCell(4).getCellType()).isEqualTo(CellType.BLANK);assertThat(e.getRow(8).getCell(1).getCellType()).isEqualTo(CellType.BLANK);
   assertThat(c.getRow(13).getCell(0).getStringCellValue()).doesNotContain("\n");assertThat(c.getRow(13).getCell(0).getCellStyle().getWrapText()).isFalse();
   assertThat(c.getRow(21).getCell(3).getStringCellValue()).isEqualTo(CustomsWorkbook.specification(second));
   assertThat(e.getRow(1).getCell(0).getStringCellValue()).isEqualTo("HS");assertThat(e.getRow(13).getCell(0).getStringCellValue()).isEqualTo("HS");
   assertThat(e.getRow(7).getCell(1).getStringCellValue()).isEqualTo("SYN-BLACK");assertThat(e.getRow(19).getCell(1).getStringCellValue()).isEqualTo("SYN-GRAY");
   assertThat(e.getColumnWidth(0)).isEqualTo(template.getSheet("申报要素").getColumnWidth(0));assertThat(e.getColumnWidth(1)).isEqualTo(template.getSheet("申报要素").getColumnWidth(1));
   for(var row:e)for(var cell:row)assertThat(cell.toString()).doesNotContain("商品 / SKU","计量单位");
  }
 }
 @Test void exportsFiveSheetsWithGrowingRowsExactTotalsAndCorrectInvoiceDate()throws Exception{
  String token=user("ADMIN");for(int count:new int[]{1,2,3,12}){var in=input(count);String id=create(token,in);byte[] bytes=mvc.perform(get("/api/v1/customs-documents/"+id+"/export").header("Authorization",token)).andExpect(status().isOk()).andExpect(header().string("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse().getContentAsByteArray();
   try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(w.getNumberOfSheets()).isEqualTo(5);var c=w.getSheet("报关单");var v=w.getSheet("发票");var p=w.getSheet("装箱单");var t=w.getSheet("合同");assertThat(v.getRow(8).getCell(6).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(java.time.LocalDate.of(2026,11,3));assertThat(v.getRow(8).getCell(6).getCellStyle().getDataFormatString()).isEqualTo("yyyy/m/d");assertThat(v.getRow(5).getCell(6).getStringCellValue()).isEqualTo(in.documentNo());assertThat(t.getRow(2).getCell(11).getStringCellValue()).isEqualTo("SYN-CONTRACT");assertThat(c.getRow(11).getCell(4).getNumericCellValue()).isEqualTo(count*2);assertThat(v.getRow(14+count).getCell(6).getNumericCellValue()).isCloseTo(count*3.71,within(.00001));assertThat(t.getRow(21+count).getCell(10).getNumericCellValue()).isCloseTo(count*3.71,within(.00001));assertThat(p.getRow(16+count).getCell(7).getNumericCellValue()).isEqualTo(count*5);assertThat(v.getRow(13+count).getCell(2).getStringCellValue()).isEqualTo("SYN-"+(count-1));for(var sheet:w){sheet.validateMergedRegions();for(var row:sheet)for(var cell:row)assertThat(cell.getCellType()==CellType.ERROR || cell.getCellType()==CellType.FORMULA&&cell.getCachedFormulaResultType()==CellType.ERROR).isFalse();}}
   if(count==3)java.nio.file.Files.write(exportDirectory.resolve("export-test.xlsx"),bytes);
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
 @Test void structuredElementsExportInOrderAndKeepMultilineOtherInOneCell()throws Exception{
  var row=item("SYN-ELEMENT");row.putAll(Map.of("elementsVersion","1","brandType","无品牌","exportBenefit","不享惠","floorCovering","是","fiberComposition","合成纤维","dimensions","30×40cm","weavingMethod","合成方法","fixingMethod","合成方式","other","第一行\n第二行"));
  byte[] bytes=exporter.export(new CustomsDocuments.Document("id","SYN","DRAFT",fields(),List.of(row),0,""));try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))){var sheet=w.getSheet("申报要素");assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("品牌类型");assertThat(sheet.getRow(10).getCell(0).getStringCellValue()).isEqualTo("GTIN");assertThat(sheet.getRow(12).getCell(0).getStringCellValue()).isEqualTo("其他");assertThat(sheet.getRow(12).getCell(1).getStringCellValue()).isEqualTo("第一行\n第二行");}
 }
 @Test void mixedUnitsAreNotAddedAndTextIsNeverAnExcelFormula()throws Exception{
  var rows=List.of(item("=1+1"),item("SYN-2"));rows.get(1).put("unit","SETS");byte[] bytes=exporter.export(new CustomsDocuments.Document("id","SYN","DRAFT",fields(),rows,0,""));try(var w=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertThat(w.getSheet("合同").getRow(19).getCell(4).getCellType()).isEqualTo(CellType.STRING);assertThat(w.getSheet("发票").getRow(14).getCell(2).getCellFormula()).isEqualTo("合同!E20");assertThat(w.getSheet("发票").getRow(14).getCell(2).getStringCellValue()).isEqualTo("=1+1");assertThat(w.getSheet("发票").getRow(16).getCell(3).getStringCellValue()).isEqualTo("见明细");}
 }
 private String user(String role)throws Exception{String id=UUID.randomUUID().toString(),password="Synthetic-Customs-42!";jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic customs QA",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role.equals("NONE")?"SELECTION":role);if(!role.equals("NONE"))jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();}
}
