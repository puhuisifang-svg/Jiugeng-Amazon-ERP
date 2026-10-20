package com.jiugeng.erp.cartonlabel;

import static com.jiugeng.erp.cartonlabel.LabelTypes.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class LabelControllerTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;
    static final String ROOT="/api/v1/carton-labels";
    record Fixture(String customer,String shipment) {}
    Config config() {
        return new Config("合成客户 & 供应商发货外包装唛头表",14,true,"示例地点",List.of(
            new Row(Field.SKU,"SKU编码\n（客户产品编码）",20,18),new Row(Field.QUANTITY,"发货数量\n（套数）",14,20),
            new Row(Field.BOXES,"发货件数\n（箱数）",14,20),new Row(Field.PURCHASER,"采购人",14,18)));
    }
    Fixture fixture() throws Exception {
        String c=UUID.randomUUID().toString(),p=UUID.randomUUID().toString(),v=UUID.randomUUID().toString(),s=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer(id,name,status,created_by,updated_by) VALUES(?,'合成客户','ACTIVE','test','test')",c);
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,'合成产品','ACTIVE','test','test')",p,p,p);
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?,'合成产品黑色','ACTIVE',?)",v,p,v,v,v);
        jdbc.update("INSERT INTO customer_product(id,customer_id,product_id,variant_id,sku_code,product_name,price_unit,customer_product_code,created_by,updated_by) VALUES(?,?,?,?,?,'合成产品黑色','套','SYN2670','test','test')",v,c,p,v,v);
        String snapshot=json.writeValueAsString(Map.of("id",v,"customerId",c,"customerName","合成客户","productId",p,"variantId",v,"customerProductCode","SYN2670","requirements","合成要求"));
        jdbc.update("INSERT INTO dashboard_today_shipment(id,ship_date,product_id,variant_id,customer_product_id,customer_snapshot,product_code_snapshot,product_name_snapshot,quantity,box_count,latest_ship_at,status,created_by,updated_by) VALUES(?,CURRENT_DATE,?,?,?,?,?,'合成产品 黑色 457cm x 20cm',80,2,CURRENT_TIMESTAMP,'ACTIVE','test','test')",s,p,v,v,snapshot,v);
        return new Fixture(c,s);
    }
    Template template(Fixture f,String token) throws Exception {
        return json.readValue(mvc.perform(post(ROOT+"/customers/"+f.customer()+"/templates").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new TemplateInput("合成模板",config(),true,true,null))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),Template.class);
    }
    Batch batch(Fixture f,Template t) {
        return new Batch(List.of(new Entry(f.shipment(),0,t.id(),t.version(),"合成产品 黑色 457cm x 20cm","SYN2670","示例采购人","SYN-ORDER-001",
            "示例收货地点","示例快递","SYN123456")),UUID.randomUUID().toString());
    }
    byte[] preview(Batch batch,String token) throws Exception {
        return mvc.perform(post(ROOT+"/preview").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(batch)))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
    }
    Export generate(Batch batch,String token) throws Exception {
        return json.readValue(mvc.perform(post(ROOT+"/exports").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(batch)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),Export.class);
    }
    byte[] download(String id,String token) throws Exception {
        return mvc.perform(get(ROOT+"/exports/"+id+"/pdf").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }
    @Test void singlePageSettingPersistsAndCountsActualPagesWithoutChangingBoxTotals() throws Exception {
        var f=fixture();var other=fixture();String token=login("ADMIN");var t=template(f,token);var byBox=template(other,token);
        var c=config();var single=new Config(c.title(),c.titleSize(),c.color(),c.defaultDestination(),c.rows(),true);
        t=json.readValue(mvc.perform(put(ROOT+"/customers/"+f.customer()+"/templates/"+t.id()).header("Authorization",token)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new TemplateInput("单页",single,true,true,t.version()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.config.singlePage").value(true))
            .andReturn().getResponse().getContentAsString(),Template.class);
        mvc.perform(get(ROOT+"/customers/"+f.customer()+"/templates").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].config.singlePage").value(true));
        // More than 500 boxes is allowed if the template generates only one page.
        jdbc.update("UPDATE dashboard_today_shipment SET box_count=600 WHERE id=?",f.shipment());
        var one=batch(f,t);
        try(var doc=Loader.loadPDF(preview(one,token))) {
            assertThat(doc.getNumberOfPages()).isOne();
            assertThat(new PDFTextStripper().getText(doc)).contains("600","80");
            if(Boolean.getBoolean("label.qa")) {
                var out=java.nio.file.Path.of("target/label-qa");java.nio.file.Files.createDirectories(out);
                javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,200),"png",out.resolve("single-page.png").toFile());
            }
        }
        var combined=new Batch(List.of(one.items().getFirst(),batch(other,byBox).items().getFirst()),UUID.randomUUID().toString());
        var saved=generate(combined,token);assertThat(saved.pages()).isEqualTo(3);
        try(var doc=Loader.loadPDF(download(saved.id(),token))) { assertThat(doc.getNumberOfPages()).isEqualTo(3); }
        // Old configurations lacking the new flag retain the original by-box behavior.
        var legacy=json.valueToTree(config());((com.fasterxml.jackson.databind.node.ObjectNode)legacy).remove("singlePage");
        jdbc.update("UPDATE carton_label_template SET config=? WHERE id=?",json.writeValueAsString(legacy),byBox.id());
        try(var doc=Loader.loadPDF(preview(batch(other,byBox),token))) { assertThat(doc.getNumberOfPages()).isEqualTo(2); }
    }
    @Test void generatesIdentical100mmPagesAndRetainsImmutablePdfAndManualFields() throws Exception {
        var f=fixture();String token=login("ADMIN");var t=template(f,token);var batch=batch(f,t);
        byte[] preview=preview(batch,token);
        try(var doc=Loader.loadPDF(preview)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(2);
            assertThat(doc.getPage(0).getMediaBox().getWidth()).isCloseTo(283.46457f,org.assertj.core.data.Offset.offset(.01f));
            assertThat(doc.getPage(0).getMediaBox().getHeight()).isCloseTo(283.46457f,org.assertj.core.data.Offset.offset(.01f));
            var stripper=new PDFTextStripper();stripper.setStartPage(1);stripper.setEndPage(1);String first=stripper.getText(doc);
            stripper.setStartPage(2);stripper.setEndPage(2);
            assertThat(stripper.getText(doc)).isEqualTo(first);
            assertThat(first).contains("80","2","SYN2670","示例采购人","示例收货地点","SYN-ORDER-001");
            // Only the title, eight body cells and footer have borders; header has none.
            var operators=new org.apache.pdfbox.pdfparser.PDFStreamParser(doc.getPage(0)).parse();
            assertThat(operators.stream().filter(o->o instanceof org.apache.pdfbox.contentstream.operator.Operator op
                    && op.getName().equals("S")).count()).isEqualTo(10);
            for(var name:doc.getPage(0).getResources().getFontNames())assertThat(doc.getPage(0).getResources().getFont(name).isEmbedded()).isTrue();
            if(Boolean.getBoolean("label.qa")) {
                var out=java.nio.file.Path.of("target/label-qa");java.nio.file.Files.createDirectories(out);
                java.nio.file.Files.write(out.resolve("sample.pdf"),preview);
                javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImageWithDPI(0,200),"png",out.resolve("sample.png").toFile());
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carton_label_export",Long.class)).isZero();
        var exported=generate(batch,token);assertThat(exported.pages()).isEqualTo(2);
        assertThat(generate(batch,token).id()).isEqualTo(exported.id());
        byte[] original=download(exported.id(),token);
        jdbc.update("UPDATE carton_label_template SET version=version+1,name='改名' WHERE id=?",t.id());
        jdbc.update("UPDATE dashboard_today_shipment SET quantity=90,version=version+1 WHERE id=?",f.shipment());
        assertThat(download(exported.id(),token)).isEqualTo(original);
        mvc.perform(get(ROOT+"/shipments/"+f.shipment()+"/draft").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(90)).andExpect(jsonPath("$.previous.purchaser").value("示例采购人"));
        mvc.perform(get(ROOT+"/shipments/"+f.shipment()+"/history").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(exported.id()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='CARTON_LABEL_EXPORT' AND resource_id=?",Long.class,exported.id())).isOne();
    }
    @Test void enforcesCustomerTemplateOwnershipVersionsPageLimitsAndRequiredFields() throws Exception {
        var f=fixture();String token=login("ADMIN");var t=template(f,token);var batch=batch(f,t);
        var other=fixture();var otherTemplate=template(other,token);var e=batch.items().getFirst();
        var wrong=new Entry(e.shipmentId(),0,otherTemplate.id(),0,e.productName(),e.customerCode(),e.purchaser(),"","","","");
        mvc.perform(post(ROOT+"/exports").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Batch(List.of(wrong),UUID.randomUUID().toString())))).andExpect(status().isBadRequest());
        var blank=new Entry(e.shipmentId(),0,t.id(),0,e.productName(),e.customerCode()," ","","","","");
        mvc.perform(post(ROOT+"/exports").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Batch(List.of(blank),"blank")))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE dashboard_today_shipment SET box_count=501 WHERE id=?",f.shipment());
        mvc.perform(post(ROOT+"/preview").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(batch))).andExpect(status().isBadRequest());
        jdbc.update("UPDATE dashboard_today_shipment SET box_count=2,version=1 WHERE id=?",f.shipment());
        mvc.perform(post(ROOT+"/exports").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(batch))).andExpect(status().isConflict());
        jdbc.update("UPDATE dashboard_today_shipment SET version=0,status='ARCHIVED' WHERE id=?",f.shipment());
        mvc.perform(post(ROOT+"/exports").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(batch))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carton_label_export",Long.class)).isZero();
    }
    @Test void defaultTemplateIsUniqueAndStaleTemplateUpdateRejected() throws Exception {
        var f=fixture();String token=login("ADMIN");var first=template(f,token);template(f,token);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carton_label_template WHERE customer_id=? AND is_default=TRUE",Long.class,f.customer())).isOne();
        mvc.perform(put(ROOT+"/customers/"+f.customer()+"/templates/"+first.id()).header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new TemplateInput("旧编辑",config(),true,true,0L)))).andExpect(status().isConflict());
        var bad=new Config("标题",14,false,"",List.of(new Row(Field.SKU,"编码",14,14),new Row(Field.SKU,"编码",14,14),new Row(Field.BOXES,"箱数",14,14),new Row(Field.PURCHASER,"采购人",14,14)));
        mvc.perform(post(ROOT+"/customers/"+f.customer()+"/templates").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new TemplateInput("重复字段",bad,true,true,null)))).andExpect(status().isBadRequest());
    }
    @Test void endpointsRequireAuthenticationAndPermissions() throws Exception {
        var f=fixture();String admin=login("ADMIN"),reader=login("SELECTION");var t=template(f,admin);
        mvc.perform(get(ROOT+"/shipments/"+f.shipment()+"/draft")).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT+"/shipments/"+f.shipment()+"/draft").header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(post(ROOT+"/exports").header("Authorization",reader).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(batch(f,t)))).andExpect(status().isForbidden());
        mvc.perform(post(ROOT+"/customers/"+f.customer()+"/templates").header("Authorization",reader).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new TemplateInput("模板",config(),true,true,null)))).andExpect(status().isForbidden());
        var export=generate(batch(f,t),admin);
        mvc.perform(get(ROOT+"/exports/"+export.id()+"/pdf").header("Authorization",reader)).andExpect(status().isForbidden());
    }
    @Test void mixedCustomersUseTheirOwnTemplatesAndRejectPartialOrChangedRequests() throws Exception {
        String token=login("ADMIN");var a=fixture();var b=fixture();var ta=template(a,token);var tb=template(b,token);
        var first=batch(a,ta).items().getFirst();var second=batch(b,tb).items().getFirst();
        var both=new Batch(List.of(first,second),"mixed-export");
        var saved=generate(both,token);assertThat(saved.pages()).isEqualTo(4);
        try(var doc=Loader.loadPDF(download(saved.id(),token))){assertThat(doc.getNumberOfPages()).isEqualTo(4);}
        mvc.perform(post(ROOT+"/exports").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new Batch(List.of(first),"mixed-export")))).andExpect(status().isConflict());
        jdbc.update("UPDATE carton_label_template SET version=1 WHERE id=?",tb.id());
        mvc.perform(post(ROOT+"/exports").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new Batch(List.of(first,second),"stale-template")))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carton_label_export",Long.class)).isOne();
        var tooLong=new Entry(first.shipmentId(),0,ta.id(),0,first.productName(),"长".repeat(128),first.purchaser(),"","","","");
        mvc.perform(post(ROOT+"/preview").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new Batch(List.of(tooLong),"long-text")))).andExpect(status().isBadRequest());
    }
    String login(String role)throws Exception {
        String id=UUID.randomUUID().toString(),password="Synthetic-Label-42";
        jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);
        return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();
    }
}
