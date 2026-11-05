package com.jiugeng.erp.forwarder;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ForwarderDocumentsTest {
    static final Path TEMPLATES=templates();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("erp.forwarder.template-dir",()->TEMPLATES.toString());}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json; @Autowired ForwarderWorkbook exporter;
    @AfterEach void clean(){jdbc.update("DELETE FROM forwarder_document");}
    static Path templates(){try{
        Path dir=Files.createTempDirectory("forwarder-test-");
        for(boolean k:List.of(true,false))try(Workbook w=k?new HSSFWorkbook():new XSSFWorkbook()){
            var s=w.createSheet(k?"美线+加线-发票导入":"运单信息");var style=w.createCellStyle();style.setWrapText(true);style.setBorderBottom(BorderStyle.THIN);
            for(int r=0;r<50;r++){var row=s.createRow(r);row.setHeightInPoints(70);for(int c=0;c<(k?23:16);c++){row.createCell(c).setCellStyle(style);s.setColumnWidth(c,4000);}}
            s.getRow(0).getCell(0).setCellValue("SYNTHETIC TEMPLATE");s.addMergedRegion(new CellRangeAddress(0,0,0,k?9:15));
            s.getRow(k?14:11).getCell(k?9:4).setCellFormula("1+1");s.getRow(20).getCell(0).setCellValue("SAMPLE MUST BE REMOVED");
            if(k){var channels=w.createSheet("渠道");channels.createRow(0);var r=channels.createRow(1);r.createCell(4).setCellValue("美国海运");r.createCell(5).setCellValue("Synthetic channel");var a=w.createSheet("《美国AMZ地址库》");a.createRow(0);var ar=a.createRow(1);ar.createCell(1).setCellValue("SYN1");ar.createCell(6).setCellValue("555-010-2040");ar.createCell(8).setCellValue("Synthetic street");w.createSheet("《欧洲AMZ地址库》");}
            else{var imp=w.createSheet("进口商");imp.createRow(0).createCell(0).setCellValue("Importer");var a=w.createSheet("仓库代码地址");a.createRow(0);a.createRow(1).createCell(0).setCellValue("SYN1");}
            int picture=w.addPicture(Base64.getDecoder().decode(png().substring(22)),Workbook.PICTURE_TYPE_PNG);var anchor=w.getCreationHelper().createClientAnchor();anchor.setRow1(k?14:11);anchor.setRow2(k?15:12);anchor.setCol1(k?16:14);anchor.setCol2(k?17:15);s.createDrawingPatriarch().createPicture(anchor,picture);
            try(var out=Files.newOutputStream(dir.resolve(k?"kaiqi-v1.xls":"huamao-v1.xlsx"))){w.write(out);}
        }return dir;
    }catch(IOException e){throw new UncheckedIOException(e);}}
    static String png(){try{var im=new java.awt.image.BufferedImage(12,8,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(im,"png",out);return "data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());}catch(IOException e){throw new UncheckedIOException(e);}}
    static Map<String,String> fields(){var f=new HashMap<String,String>();for(String k:ForwarderDocuments.HEADERS)f.put(k,"");for(String k:List.of("waybill","consignee","company","address","city","region","postalCode","shipper","shipperCompany","shipperAddress","shipperCity","shipperRegion","shipperPostalCode","shipperPhone"))f.put(k,"Synthetic "+k);f.putAll(Map.of("warehouse","SYN1","country","US","channel","Synthetic channel","customsType","退税报关","insured","否","battery","不带电","magnet","不带磁","address2","Suite synthetic","importer","Synthetic importer"));return f;}
    static Map<String,String> item(String sku){var r=new HashMap<String,String>();for(String k:ForwarderDocuments.ITEMS)r.put(k,"");r.putAll(Map.of("sku",sku,"fba","SYN-FBA","boxStart","1","boxCount","2","perBox","3","price","1.25","grossWeight","5","netWeight","4","length","20","width","15"));r.putAll(Map.of("height","10","name","合成测试商品","nameEn","Synthetic goods","hsCode","0123456789","material","Synthetic material","usage","Synthetic usage","image",png()));return r;}
    ForwarderDocuments.Input input(String carrier,List<Map<String,String>> rows){return new ForwarderDocuments.Input("SYN-"+UUID.randomUUID(),carrier,"DRAFT",fields(),rows,null);}
    String create(String token,ForwarderDocuments.Input in)throws Exception{return json.readTree(mvc.perform(post("/api/v1/forwarder-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();}
    byte[] export(String token,String id)throws Exception{return mvc.perform(get("/api/v1/forwarder-documents/"+id+"/export").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();}
    @Test void exportsOriginalWorkbookTypesWithMultipleSkusAndNoSampleOrFormula()throws Exception{
        String token=user("ADMIN");for(String carrier:List.of("KAIQI","HUAMAO")){
            byte[] original=Files.readAllBytes(TEMPLATES.resolve(carrier.equals("KAIQI")?"kaiqi-v1.xls":"huamao-v1.xlsx"));
            var second=item("=1+1");second.put("boxStart","3");second.put("boxCount","1");var in=input(carrier,List.of(item("SYN-A"),second));String id=create(token,in);
            mvc.perform(get("/api/v1/forwarder-documents").param("q",in.documentNo()).header("Authorization",token)).andExpect(jsonPath("$.items[0].cartons").value(3)).andExpect(jsonPath("$.items[0].amount").value("11.25"));
            try(var w=WorkbookFactory.create(new ByteArrayInputStream(export(token,id)))){
                boolean k=carrier.equals("KAIQI");assertThat(w instanceof HSSFWorkbook).isEqualTo(k);assertThat(w.getNumberOfSheets()).isEqualTo(k?4:3);var s=w.getSheetAt(0);s.validateMergedRegions();assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("SYNTHETIC TEMPLATE");assertThat(s.getColumnWidth(0)).isEqualTo(4000);
                assertThat(ForwarderWorkbook.cell(s,k?"B5":"F3").getNumericCellValue()).isEqualTo(3);assertThat(s.getRow(k?14:11).getCell(k?6:5).getStringCellValue()).isEqualTo("0123456789");assertThat(s.getRow(k?14:11).getCell(0).getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.THIN);
                if(k){assertThat(s.getRow(14).getCell(9).getNumericCellValue()).isEqualTo(3);assertThat(s.getRow(14).getCell(10).getNumericCellValue()).isEqualTo(3.75);assertThat(s.getRow(15).getCell(0).getNumericCellValue()).isEqualTo(2);assertThat(s.getRow(16).getCell(0).getNumericCellValue()).isEqualTo(3);}
                else{assertThat(s.getRow(12).getCell(15).getStringCellValue()).isEqualTo("=1+1");assertThat(ForwarderWorkbook.cell(s,"H6").getStringCellValue()).isEqualTo("Synthetic address, Suite synthetic");assertThat(ForwarderWorkbook.cell(w.getSheet("进口商"),"B2").getStringCellValue()).isEqualTo("Synthetic importer");assertThat(s.getRow(11).getCell(4).getNumericCellValue()).isEqualTo(5);}
                int count=0;for(Shape shape:s.getDrawingPatriarch()){count++;assertThat(shape).isInstanceOf(Picture.class);var a=((Picture)shape).getClientAnchor();assertThat(a.getRow1()).isGreaterThanOrEqualTo(k?14:11);assertThat(a.getRow2()).isEqualTo(a.getRow1());assertThat(a.getDx2()).isGreaterThan(a.getDx1());}assertThat(count).isEqualTo(k?3:2);
                for(Row r:s)for(Cell c:r){assertThat(c.getCellType()).isNotIn(CellType.ERROR,CellType.FORMULA);assertThat(c.toString()).doesNotContain("SAMPLE MUST BE REMOVED");}
            }
            assertThat(Files.readAllBytes(TEMPLATES.resolve(carrier.equals("KAIQI")?"kaiqi-v1.xls":"huamao-v1.xlsx"))).isEqualTo(original);
        }
    }
    @Test void mixedCartonsCountOnceAndOverlappingInconsistentRangesAreRejected()throws Exception{
        String token=user("ADMIN");var in=input("HUAMAO",List.of(item("SYN-A"),item("SYN-B")));String id=create(token,in);
        mvc.perform(get("/api/v1/forwarder-documents").param("q",in.documentNo()).header("Authorization",token)).andExpect(jsonPath("$.items[0].cartons").value(2)).andExpect(jsonPath("$.items[0].amount").value("15.00"));
        try(var w=WorkbookFactory.create(new ByteArrayInputStream(export(token,id)))){var s=w.getSheetAt(0);assertThat(s.getMergedRegions()).contains(new CellRangeAddress(11,12,3,3));assertThat(s.getRow(12).getCell(3).getCellType()).isEqualTo(CellType.BLANK);}
        in.items().get(1).put("grossWeight","6");mvc.perform(post("/api/v1/forwarder-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isBadRequest());
    }
    @Test void draftsRetainInputsButIncompleteOrInvalidDocsCannotBeExported()throws Exception{
        String token=user("ADMIN");var in=new ForwarderDocuments.Input("SYN-DRAFT","KAIQI","DRAFT",Map.of("country","US"),List.of(),null);String id=create(token,in);
        mvc.perform(get("/api/v1/forwarder-documents/"+id+"/export").header("Authorization",token)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("运单号")));
        for(String bad:List.of("netWeight","boxCount","image")){var row=item("SYN");row.put(bad,bad.equals("image")?"data:image/png;base64,invalid":bad.equals("boxCount")?"1.5":"99");mvc.perform(post("/api/v1/forwarder-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input("KAIQI",List.of(row))))).andExpect(status().isBadRequest());}
    }
    @Test void enforcesPermissionsOptimisticVersionsAuditAndDeleteReusesNumber()throws Exception{
        String token=user("ADMIN"),none=user("NONE");var in=input("KAIQI",List.of(item("SYN")));String id=create(token,in);
        mvc.perform(get("/api/v1/forwarder-documents")).andExpect(status().isUnauthorized());
        for(String path:List.of("","/options?carrier=KAIQI","/"+id,"/"+id+"/export"))mvc.perform(get("/api/v1/forwarder-documents"+path).header("Authorization",none)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/forwarder-documents").header("Authorization",none).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/forwarder-documents/"+id+"?version=0").header("Authorization",none)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/forwarder-documents").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(in))).andExpect(status().isConflict());
        var update=new ForwarderDocuments.Input(in.documentNo(),in.carrier(),"READY",in.fields(),in.items(),0L);
        mvc.perform(put("/api/v1/forwarder-documents/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/forwarder-documents/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(update))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/forwarder-documents/"+id+"?version=0").header("Authorization",token)).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/forwarder-documents/"+id+"?version=1").header("Authorization",token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/forwarder-documents/"+id).header("Authorization",token)).andExpect(status().isNotFound());create(token,in);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='FORWARDER_DOCUMENT_DELETE'",Integer.class,id)).isEqualTo(1);
    }
    @Test void optionsUseCorrectPhoneColumnAndMissingTemplateHasClearError()throws Exception{
        String token=user("ADMIN");mvc.perform(get("/api/v1/forwarder-documents/options?carrier=KAIQI").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.channels[0]").value("Synthetic channel")).andExpect(jsonPath("$.addresses[0].phone").value("555-010-2040"));
        assertThatThrownBy(()->new ForwarderWorkbook(TEMPLATES.resolve("missing").toString()).options("KAIQI")).hasMessageContaining("未部署");
    }
    private String user(String role)throws Exception{String id=UUID.randomUUID().toString(),password="Synthetic-Forwarder-42!";jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic forwarder QA",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role.equals("NONE")?"SELECTION":role);if(!role.equals("NONE"))jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();}
}
