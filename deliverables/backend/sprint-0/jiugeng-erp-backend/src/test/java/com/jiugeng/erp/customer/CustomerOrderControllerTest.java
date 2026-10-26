package com.jiugeng.erp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CustomerOrderControllerTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;@Autowired Clock clock;
    static final String BASE="/api/v1/customer-orders";
    String date(){return LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))).toString();}
    @Test void multiItemOrderHasOneInvoiceWithImmutableTitleAndServerAmounts() throws Exception {
        String token=login("ADMIN"),cp=fixture();var input=input(cp);
        input.put("totalAmount",0);var order=body(send(token,BASE,input).andExpect(status().isOk()));String id=order.path("id").asText();
        assertThat(new java.math.BigDecimal(order.path("totalAmount").asText())).isEqualByComparingTo("30.30");
        assertThat(order.path("lines").size()).isEqualTo(2);
        send(token,BASE+"/"+id+"/invoice",Map.of()).andExpect(status().isConflict());
        confirm(token,id);
        jdbc.update("UPDATE customer SET invoice_title='修改后的抬头' WHERE id=?",cp);
        var invoice=body(send(token,BASE+"/"+id+"/invoice",Map.of()).andExpect(status().isOk()));
        assertThat(invoice.path("snapshot").path("invoiceTitle").path("invoiceTitle").asText()).isEqualTo("合成抬头");
        assertThat(invoice.path("snapshot").path("lines").size()).isEqualTo(2);
        send(token,BASE+"/"+id+"/invoice",Map.of()).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(invoice.path("id").asText()));
        String invoicePath=BASE+"/invoices/"+invoice.path("id").asText();
        mvc.perform(put(invoicePath).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("version",0,"invoiced",true,"invoiceNumber","SYN-INV","invoiceDate",date())))).andExpect(status().isOk());
        mvc.perform(put(invoicePath).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("version",1,"invoiced",false)))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order_invoice WHERE order_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void fractionalAllocationRoundTripAndDispatchNeverDoubleCount() throws Exception {
        String token=login("ADMIN"),cp=fixture();var order=body(send(token,BASE,input(cp)).andExpect(status().isOk()));String id=order.path("id").asText();confirm(token,id);
        String line=order.path("lines").get(0).path("id").asText();var request=arrange(line,"0.500");
        var allocation=body(arrange(token,id,request,"same-key").andExpect(status().isOk())).get(0);
        arrange(token,id,request,"same-key").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order_allocation WHERE order_id=?",Integer.class,id)).isEqualTo(1);
        arrange(token,id,arrange(line,"1.001"),"too-many").andExpect(status().isConflict());
        String pending=allocation.path("pendingId").asText();String pendingPath="/api/v1/dashboard-maintenance/pending-shipments/"+pending;
        var transfer=Map.of("quantity",0.5,"boxCount",1,"latestShipTime",date()+"T23:59:00","version",0);
        var today=body(send(token,pendingPath+"/transfer-to-today",transfer).andExpect(status().isOk()));String todayId=today.path("id").asText();
        assertThat(today.path("quantity").decimalValue()).isEqualByComparingTo("0.5");assertThat(today.path("quantityUnit").asText()).isEqualTo("米");
        var returned=body(send(token,"/api/v1/dashboard-maintenance/today-shipments/"+todayId+"/transfer-to-pending",Map.of("version",0,"reason","合成调整")).andExpect(status().isOk()));
        assertThat(returned.path("id").asText()).isEqualTo(pending);
        var todayAgain=body(send(token,pendingPath+"/transfer-to-today",Map.of("quantity",0.5,"boxCount",1,"latestShipTime",date()+"T23:59:00","version",returned.path("version").asLong())).andExpect(status().isOk()));
        send(token,"/api/v1/shipping/today-shipments/"+todayId+"/dispatch",Map.of("version",todayAgain.path("version").asLong())).andExpect(status().isOk());
        var updated=body(mvc.perform(get(BASE+"/"+id).header("Authorization",token)).andExpect(status().isOk()));
        assertThat(updated.path("lines").get(0).path("shippedQuantity").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(updated.path("lines").get(0).path("allocatedQuantity").decimalValue()).isEqualByComparingTo("0");
        arrange(token,id,arrange(line,"1"),"remainder").andExpect(status().isOk());
    }
    @Test void archiveReleasesQuantityAndRejectsOversizedMutation() throws Exception {
        String token=login("ADMIN"),cp=fixture();var order=body(send(token,BASE,input(cp)).andExpect(status().isOk()));String id=order.path("id").asText();confirm(token,id);
        String line=order.path("lines").get(0).path("id").asText();var a=body(arrange(token,id,arrange(line,"1.5"),"full").andExpect(status().isOk())).get(0);
        String pending=a.path("pendingId").asText();
        var shipment=Map.of("date",date(),"productId",cp,"variantId",cp,"customerProductId",cp,"quantity",2,"boxCount",1,"latestShipTime",date()+"T23:59:00");
        mvc.perform(patch("/api/v1/dashboard-maintenance/pending-shipments/"+pending).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("shipment",shipment,"version",0)))).andExpect(status().isConflict());
        send(token,"/api/v1/dashboard-maintenance/pending-shipments/"+pending+"/archive",Map.of("version",0,"reason","测试删除")).andExpect(status().isOk());
        arrange(token,id,arrange(line,"1.5"),"again").andExpect(status().isOk());
    }
    @Test void validatesCustomerTitlePrecisionAndDuplicateOrderAndPermissions() throws Exception {
        String token=login("ADMIN"),cp=fixture();var input=input(cp);input.put("externalNumber","SYN-ORDER");
        send(token,BASE,input).andExpect(status().isOk());send(token,BASE,input).andExpect(status().isConflict());
        input.put("externalNumber","SYN-OTHER");input.put("invoiceTitle","wrong");send(token,BASE,input).andExpect(status().isBadRequest());
        input.put("invoiceTitle","合成抬头");input.put("customerId",fixture());send(token,BASE,input).andExpect(status().isBadRequest());
        send(login("SELECTION"),BASE,input(cp)).andExpect(status().isForbidden());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }
    @Test void createRetryIsIdempotentAndFiltersUseDerivedStates() throws Exception {
        String token=login("ADMIN"),cp=fixture();var input=input(cp);String key=UUID.randomUUID().toString();
        var first=body(mvc.perform(post(BASE).header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isOk()));
        mvc.perform(post(BASE).header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first.path("id").asText()));
        input.put("remark","changed");
        mvc.perform(post(BASE).header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input))).andExpect(status().isConflict());
        mvc.perform(get(BASE).param("customerId",cp).param("status","DRAFT").param("shipping","UNSHIPPED").param("invoiceStatus","NOT_GENERATED").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get(BASE).param("customerId",cp).param("status","COMPLETED").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
    }
    @Test void orderEditsPreserveTitleSnapshotAndRequireInvoiceRefresh() throws Exception {
        String token=login("ADMIN"),cp=fixture();var order=body(send(token,BASE,input(cp)).andExpect(status().isOk()));String id=order.path("id").asText();confirm(token,id);
        var invoice=body(send(token,BASE+"/"+id+"/invoice",Map.of()).andExpect(status().isOk()));
        jdbc.update("UPDATE customer SET name='新名称',invoice_title='新抬头' WHERE id=?",cp);
        var edit=edit(order,1);edit.put("remark","更新备注");
        var saved=body(mvc.perform(put(BASE+"/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(edit))).andExpect(status().isOk()));
        assertThat(saved.path("customerName").asText()).isEqualTo("合成客户");assertThat(saved.path("invoiceTitle").path("invoiceTitle").asText()).isEqualTo("合成抬头");
        String path=BASE+"/invoices/"+invoice.path("id").asText();
        var issue=new HashMap<String,Object>(Map.of("version",0,"invoiced",true,"invoiceNumber","SYN-INV","invoiceDate",date()));
        mvc.perform(put(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(issue))).andExpect(status().isConflict());
        issue.put("refreshFromOrder",true);
        mvc.perform(put(path).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(issue))).andExpect(status().isOk());
        var changed=edit(saved,2);var lines=mapper.createArrayNode();for(var line:saved.path("lines")){var d=line.path("details").deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)d).put("id",line.path("id").asText()).put("quantity",2);lines.add(d);}changed.put("lines",lines);
        mvc.perform(put(BASE+"/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(changed))).andExpect(status().isConflict());
    }
    @Test void shipmentEditsSynchronizeOrderPlanAndStatisticsStayOrderScoped() throws Exception {
        String token=login("ADMIN"),cp=fixture();var order=body(send(token,BASE,input(cp)).andExpect(status().isOk()));String id=order.path("id").asText();confirm(token,id);
        var a=body(arrange(token,id,arrange(order.path("lines").get(0).path("id").asText(),"1"),"plan").andExpect(status().isOk())).get(0);
        String pending=a.path("pendingId").asText();
        var shipment=Map.of("date",date(),"productId",cp,"variantId",cp,"customerProductId",cp,"quantity",1.25,"boxCount",3,"remark","新的包装备注","latestShipTime",date()+"T23:59:00");
        mvc.perform(patch("/api/v1/dashboard-maintenance/pending-shipments/"+pending).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("shipment",shipment,"version",0)))).andExpect(status().isOk());
        mvc.perform(get(BASE+"/"+id+"/arrangements").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].details.boxCount").value(3)).andExpect(jsonPath("$[0].details.remark").value("新的包装备注")).andExpect(jsonPath("$[0].details.quantity").value(1.25));
        mvc.perform(get("/api/v1/shipping/statistics").param("from",date()).param("to",date()).param("orderId",id).header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.summary.records").value(0));
    }
    @Test void documentsValidateContentsPermissionsAndOwnership() throws Exception {
        String token=login("ADMIN"),cp=fixture();String id=body(send(token,BASE,input(cp)).andExpect(status().isOk())).path("id").asText();String path=BASE+"/"+id+"/documents/ORDER";
        var file=new org.springframework.mock.web.MockMultipartFile("file","synthetic.pdf","application/pdf","%PDF-1.4\nsynthetic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var document=body(mvc.perform(multipart(path).file(file).header("Authorization",token)).andExpect(status().isOk()));
        mvc.perform(get(path+"/"+document.path("id").asText()).header("Authorization",token)).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().bytes(file.getBytes()));
        mvc.perform(multipart(path).file(new org.springframework.mock.web.MockMultipartFile("file","fake.pdf","application/pdf","not-pdf".getBytes())).header("Authorization",token)).andExpect(status().isBadRequest());
        mvc.perform(multipart(path).file(file).header("Authorization",login("SELECTION"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE+"/"+id+"/documents/INVOICE").header("Authorization",token)).andExpect(status().isBadRequest());
        String other=body(send(token,BASE,input(cp)).andExpect(status().isOk())).path("id").asText();
        mvc.perform(get(BASE+"/"+other+"/documents/ORDER/"+document.path("id").asText()).header("Authorization",token)).andExpect(status().isNotFound());
    }
    Map<String,Object> edit(JsonNode order,long version) {
        Map<String,Object> value=mapper.convertValue(order.path("details"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        value.put("version",version);var lines=mapper.createArrayNode();for(var line:order.path("lines")){var d=(com.fasterxml.jackson.databind.node.ObjectNode)line.path("details").deepCopy();d.put("id",line.path("id").asText());lines.add(d);}value.put("lines",lines);return value;
    }
    void confirm(String token,String id)throws Exception {send(token,BASE+"/"+id+"/transition",Map.of("version",0,"action","CONFIRM")).andExpect(status().isOk());}
    Map<String,Object> arrange(String line,String qty) {return Map.of("version",1,"recipient","合成收货人","phone","SYN-PHONE","address","合成地址","items",List.of(Map.of("lineId",line,"quantity",qty,"boxCount",1,"plannedDate",date(),"latestDate",date(),"remark","")));}
    ResultActions arrange(String token,String id,Map<String,Object> value,String key)throws Exception {return mvc.perform(post(BASE+"/"+id+"/arrangements").header("Authorization",token).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(value)));}
    Map<String,Object> input(String cp) {
        var line=Map.of("customerProductId",cp,"name","合成商品","specification","合成规格","unit","米","quantity","1.5","netUnitPrice","10","taxRate","1","deliveryDate",date(),"remark","");
        return new HashMap<>(Map.ofEntries(Map.entry("customerId",cp),Map.entry("source","OFFLINE"),Map.entry("orderDate",date()),Map.entry("deliveryDate",date()),Map.entry("invoiceRequired",true),Map.entry("invoiceTitle","合成抬头"),Map.entry("taxNumber","SYN-TAX"),Map.entry("recipient","合成收货人"),Map.entry("phone","SYN-PHONE"),Map.entry("address","合成地址"),Map.entry("lines",List.of(line,line))));
    }
    String fixture() {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer(id,name,invoice_title,tax_number,status,created_by,updated_by) VALUES(?,'合成客户','合成抬头','SYN-TAX','ACTIVE','test','test')",id);
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,'合成父体','ACTIVE','test','test')",id,id,id);
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?,'合成商品','ACTIVE',?)",id,id,id,id,id);
        jdbc.update("INSERT INTO customer_product(id,customer_id,product_id,variant_id,sku_code,product_name,price_unit,status,created_by,updated_by) VALUES(?,?,?,?,?,'合成商品','米','ACTIVE','test','test')",id,id,id,id,id);return id;
    }
    ResultActions send(String token,String path,Object input)throws Exception {return mvc.perform(post(path).header("Authorization",token).header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)));}
    JsonNode body(ResultActions result)throws Exception {return mapper.readTree(result.andReturn().getResponse().getContentAsString());}
    String login(String role)throws Exception {
        String id=UUID.randomUUID().toString(),password="Synthetic-Orders-42";
        jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);
        return "Bearer "+body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk())).path("token").asText();
    }
}
