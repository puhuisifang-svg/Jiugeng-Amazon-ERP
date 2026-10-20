package com.jiugeng.erp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class InvoiceControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private static final String PATH="/api/v1/customers/invoices";

    @Test void createsDraftAndSnapshotWithServerCalculatedAmountsAndPrivateAudit() throws Exception {
        String token=login("PRODUCT_MATERIAL"), cp=fixture();
        var draft=body(mvc.perform(get(PATH+"/draft").param("customerProductId",cp).header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.invoiceTitle").value("合成开票抬头"))
            .andExpect(jsonPath("$.taxRate").value("1")).andExpect(jsonPath("$.netUnitPrice").isEmpty()));
        assertThat(draft.path("itemName").asText()).isEqualTo("合成蓝色SKU");
        var input=input(cp); input.remove("taxRate"); input.put("totalAmount","0.01");
        var saved=body(save(token,null,input).andExpect(status().isOk())
            .andExpect(jsonPath("$.netAmount").value("30.00")).andExpect(jsonPath("$.taxAmount").value("0.30"))
            .andExpect(jsonPath("$.totalAmount").value("30.30")).andExpect(jsonPath("$.details.taxRate").value("1.00")));
        jdbc.update("UPDATE customer SET name='修改合成客户',invoice_title='修改合成抬头' WHERE id=?",cp);
        mvc.perform(get(PATH).param("q","合成发票品名").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items[0].customerName").value("合成客户"))
            .andExpect(jsonPath("$.items[0].details.invoiceTitle").value("合成开票抬头"));
        String audit=jdbc.queryForObject("SELECT after_value FROM audit_event WHERE action='CUSTOMER_INVOICE_CREATE' AND resource_id=?",String.class,saved.path("id").asText());
        assertThat(mapper.readTree(audit).path("totalAmount").decimalValue()).isEqualByComparingTo("30.30");
        assertThat(audit).doesNotContain("SYN-TAX","SYN-BANK","SYN-TRACKING","合成开票抬头");
    }
    @Test void updatesFlagsLogisticsFiltersAndRejectsStaleVersionOrChangedCustomer() throws Exception {
        String token=login("ADMIN"), cp=fixture(); var input=input(cp);
        var saved=body(save(token,null,input).andExpect(status().isOk())); String id=saved.path("id").asText();
        input.put("version",0); input.put("shipped",true); input.put("invoiced",true); input.put("logistics","合成物流运单ABC");
        save(token,id,input).andExpect(status().isOk()).andExpect(jsonPath("$.details.version").value(1));
        save(token,id,input).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVOICE_VERSION_CONFLICT"));
        input.put("version",1); input.put("customerProductId",fixture());
        save(token,id,input).andExpect(status().isBadRequest());
        mvc.perform(get(PATH).param("q","ABC").param("shipped","true").param("invoiced","true").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get(PATH).param("invoiced","false").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        input.put("customerProductId",cp);jdbc.update("UPDATE customer SET status='DISABLED' WHERE id=?",cp);
        save(token,id,input).andExpect(status().isOk()); // History can still record subsequent shipping/invoicing.
        save(token,null,input).andExpect(status().isConflict());
        save(token,"missing",input).andExpect(status().isNotFound());
    }
    @Test void deletedCustomerProductKeepsInvoiceSnapshotAndAllowsCompletionButNotNewInvoices() throws Exception {
        String token=login("ADMIN"), cp=fixture(); var input=input(cp);
        var saved=body(save(token,null,input).andExpect(status().isOk()));
        mvc.perform(delete("/api/v1/customers/products/"+cp).param("version","0").header("Authorization",token))
            .andExpect(status().isNoContent());
        jdbc.update("UPDATE customer SET name='改名',invoice_title='改抬头' WHERE id=?",cp);
        input.put("version",0); input.put("shipped",true); input.put("invoiced",true);
        save(token,saved.path("id").asText(),input).andExpect(status().isOk())
            .andExpect(jsonPath("$.customerName").value("合成客户"))
            .andExpect(jsonPath("$.details.invoiceTitle").value("合成开票抬头"));
        save(token,null,input).andExpect(status().isConflict());
        mvc.perform(get(PATH+"/draft").param("customerProductId",cp).header("Authorization",token))
            .andExpect(status().isConflict());
    }
    @Test void handlesFractionalRoundingZeroAndLargeAmountsWithoutFloatingPointLoss() throws Exception {
        String token=login("ADMIN"), cp=fixture(); var input=input(cp);
        input.put("quantity","1.005"); input.put("netUnitPrice","1"); input.put("taxRate","100");
        save(token,null,input).andExpect(status().isOk()).andExpect(jsonPath("$.netAmount").value("1.01"))
            .andExpect(jsonPath("$.taxAmount").value("1.01")).andExpect(jsonPath("$.totalAmount").value("2.02"));
        input.put("quantity","999999999.999"); input.put("netUnitPrice","99999999.999999");
        save(token,null,input).andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value("199999999999798000.00"));
        input.put("netUnitPrice","0"); input.put("taxRate","0");
        save(token,null,input).andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value("0.00"));
    }
    @Test void validatesNumbersFieldsAndPermissionsWithoutPersistingInvalidInvoices() throws Exception {
        String token=login("ADMIN"), cp=fixture();
        for(var change:List.of(Map.of("quantity","0"),Map.of("quantity","1.0001"),Map.of("netUnitPrice","-1"),
            Map.of("netUnitPrice","1.0000001"),Map.of("taxRate","101"),Map.of("taxRate","-1"),Map.of("taxRate","1.001"),
            Map.of("invoiceTitle"," "),Map.of("unit",""),Map.of("logistics","x".repeat(1001)))) {
            var input=input(cp);input.putAll(change);save(token,null,input).andExpect(status().isBadRequest());
        }
        var missingPrice=input(cp);missingPrice.put("netUnitPrice",null);save(token,null,missingPrice).andExpect(status().isBadRequest());
        var invalid=input("missing");save(token,null,invalid).andExpect(status().isConflict());
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        String reader=login("SELECTION");
        mvc.perform(get(PATH).header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(get(PATH+"/draft").param("customerProductId",cp).header("Authorization",reader)).andExpect(status().isForbidden());
        save(reader,null,input(cp)).andExpect(status().isForbidden());
        mvc.perform(get(PATH).param("page","-1").header("Authorization",token)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_invoice",Integer.class)).isZero();
    }
    @Test void selectsOnlyCustomerTitlesAndPreservesHistoricalSnapshot() throws Exception {
        String token=login("ADMIN"), cp=fixture();
        var titles=List.of(new CustomerTypes.InvoiceTitle("合成抬头甲","T-A","地址甲","","银行甲","A"),
            new CustomerTypes.InvoiceTitle("合成抬头乙","T-B","地址乙","","银行乙","B"));
        jdbc.update("UPDATE customer SET invoice_titles=? WHERE id=?",CustomerInvoiceTitles.encode(titles),cp);
        mvc.perform(get(PATH+"/draft").param("customerProductId",cp).header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.invoiceTitle").value(""))
            .andExpect(jsonPath("$.invoiceTitles.length()").value(2));
        var input=input(cp);
        save(token,null,input).andExpect(status().isBadRequest());
        input.put("invoiceTitle","合成抬头乙");input.put("taxNumber","T-A");
        save(token,null,input).andExpect(status().isBadRequest());
        input.put("taxNumber","T-B");input.put("bankAccount","forged");
        var saved=body(save(token,null,input).andExpect(status().isOk())
            .andExpect(jsonPath("$.details.bankAccount").value("B"))
            .andExpect(jsonPath("$.details.registeredAddress").value("地址乙")));
        jdbc.update("UPDATE customer SET invoice_titles='[]' WHERE id=?",cp);
        save(token,null,input).andExpect(status().isBadRequest());
        input.put("version",0);input.put("invoiceTitle","不得覆盖历史");input.put("taxNumber","other");
        save(token,saved.path("id").asText(),input).andExpect(status().isOk())
            .andExpect(jsonPath("$.details.invoiceTitle").value("合成抬头乙"))
            .andExpect(jsonPath("$.details.bankAccount").value("B"));
        jdbc.update("UPDATE customer SET invoice_titles=? WHERE id=?",CustomerInvoiceTitles.encode(List.of(titles.get(0))),cp);
        mvc.perform(get(PATH+"/draft").param("customerProductId",cp).header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.invoiceTitle").value("合成抬头甲"));
    }

    private Map<String,Object> input(String cp) {
        return new HashMap<>(Map.ofEntries(Map.entry("customerProductId",cp),Map.entry("invoiceTitle","合成开票抬头"),
            Map.entry("taxNumber","SYN-TAX"),Map.entry("bankAccount","SYN-BANK"),Map.entry("itemName","合成发票品名"),
            Map.entry("specification","合成型号A"),Map.entry("unit","件"),Map.entry("quantity","3"),Map.entry("netUnitPrice","10"),
            Map.entry("taxRate","1"),Map.entry("shipped",false),Map.entry("invoiced",false),Map.entry("logistics","SYN-TRACKING")));
    }
    private String fixture() {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO customer(id,name,invoice_title,tax_number,bank_account,status,created_by,updated_by) VALUES(?,'合成客户','合成开票抬头','SYN-TAX','SYN-BANK','ACTIVE','test','test')",id);
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,'合成父体','ACTIVE','test','test')",id,id,id);
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?,'合成蓝色SKU','ACTIVE',?)",id,id,id,id,id);
        jdbc.update("INSERT INTO customer_product(id,customer_id,product_id,variant_id,sku_code,product_name,supply_price,price_unit,status,created_by,updated_by) VALUES(?,?,?,?,?,'合成蓝色SKU',12,'件','ACTIVE','test','test')",id,id,id,id,id);
        return id;
    }
    private ResultActions save(String token,String id,Map<String,Object> input) throws Exception {
        return mvc.perform((id==null?post(PATH):put(PATH+"/"+id)).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)));
    }
    private JsonNode body(ResultActions result) throws Exception { return mapper.readTree(result.andReturn().getResponse().getContentAsString()); }
    private String login(String role) throws Exception {
        String id=UUID.randomUUID().toString(), password="Synthetic-Invoice-42";
        jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);
        return "Bearer "+body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk())).path("token").asText();
    }
}
