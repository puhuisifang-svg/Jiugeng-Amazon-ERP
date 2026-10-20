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
class CustomerControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test void billingDefaultCustomerAndNullablePrices() throws Exception {
        String token=login("ADMIN"); var input=customer("合成开票客户");
        input.put("invoiceTitle","合成开票抬头"); input.put("taxNumber","SYN-TAX-001");
        input.put("registeredAddress","合成注册地址"); input.put("registeredPhone","SYN-PHONE");
        input.put("bankName","合成银行"); input.put("bankAccount","SYN-BANK-ACCOUNT"); input.put("defaultCustomer",true);
        var saved=body(write(token,"",null,input).andExpect(status().isOk()).andExpect(jsonPath("$.defaultCustomer").value(true))
            .andExpect(jsonPath("$.invoiceTitle").value("合成开票抬头")));
        String id=saved.path("id").asText();
        mvc.perform(get("/api/v1/customers/default").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        input.put("version",0);input.put("status","DISABLED");
        write(token,"",id,input).andExpect(status().isConflict());
        input.put("status","ACTIVE");input.put("defaultCustomer",false);
        write(token,"",id,input).andExpect(status().isConflict());
        var other=customer("另一个合成默认客户"); other.put("defaultCustomer",true);
        var next=body(write(token,"",null,other).andExpect(status().isOk()));
        mvc.perform(get("/api/v1/customers/default").header("Authorization",token)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(next.path("id").asText()));
        input.put("status","DISABLED"); input.put("version",1);
        write(token,"",id,input).andExpect(status().isOk()).andExpect(jsonPath("$.defaultCustomer").value(false));
        String audit=jdbc.queryForObject("SELECT after_value FROM audit_event WHERE action='CUSTOMER_CREATE' AND resource_id=?",String.class,id);
        assertThat(audit).doesNotContain("SYN-TAX","SYN-BANK","合成注册地址");
        var quote=quote(next.path("id").asText(),sku(),"1");quote.put("supplyPrice",null);
        write(token,"/products",null,quote).andExpect(status().isOk()).andExpect(jsonPath("$.supplyPrice").isEmpty());
    }
    @Test void customerCrudSearchAndVersionChecksPreserveContactPrivacyInAudit() throws Exception {
        String token=login("PRODUCT_MATERIAL");
        var input=customer("合成客户A");
        input.put("account1688","synthetic-buyer"); input.put("phone","TEST-PHONE"); input.put("address","合成测试地址");
        var created=body(write(token,"",null,input).andExpect(status().isOk()));
        String id=created.path("id").asText();
        mvc.perform(get("/api/v1/customers").param("q","synthetic-buyer").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));
        input.put("version",0); input.put("status","DISABLED");
        write(token,"",id,input).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        write(token,"",id,input).andExpect(status().isConflict());
        input.put("version",1); input.put("status","ACTIVE");
        write(token,"",id,input).andExpect(status().isOk());
        String logs=jdbc.queryForObject("SELECT after_value FROM audit_event WHERE resource_id=? AND action='CUSTOMER_CREATE'",String.class,id);
        assertThat(logs).doesNotContain("TEST-PHONE","合成测试地址","synthetic-buyer");
    }
    @Test void differentCustomersCanQuoteSameSkuAndUpdatesKeepPriceAudit() throws Exception {
        String token=login("ADMIN"), a=createCustomer(token), b=createCustomer(token); String[] sku=sku();
        var first=quote(a,sku,"12.50");
        var saved=body(write(token,"/products",null,first).andExpect(status().isOk())
            .andExpect(jsonPath("$.productName").value("合成SKU蓝色")));
        var second=quote(b,sku,"15.90"); write(token,"/products",null,second).andExpect(status().isOk());
        write(token,"/products",null,first).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CUSTOMER_SKU_DUPLICATE"));
        String id=saved.path("id").asText(); first.put("version",0); first.put("supplyPrice","13.20");
        write(token,"/products",id,first).andExpect(status().isOk()).andExpect(jsonPath("$.supplyPrice").value(13.20));
        write(token,"/products",id,first).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/customers/products").param("customerId",b).header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.items[0].supplyPrice").value(15.90));
        mvc.perform(get("/api/v1/customers/products").param("q","合成客户编码").param("size","1").header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.items.length()").value(1));
        String audit=jdbc.queryForObject("SELECT CONCAT(before_value,after_value) FROM audit_event WHERE action='CUSTOMER_PRODUCT_UPDATE' AND resource_id=?",String.class,id);
        assertThat(audit).contains("12.5","13.2").doesNotContain("合成特殊要求");
    }
    @Test void validatesMoneyRelationshipsStatusAndImmutableIdentity() throws Exception {
        String token=login("ADMIN"), customer=createCustomer(token); String[] sku=sku();
        var input=quote(customer,sku,"-1"); write(token,"/products",null,input).andExpect(status().isBadRequest());
        input.put("supplyPrice","1.001"); write(token,"/products",null,input).andExpect(status().isBadRequest());
        input.put("supplyPrice","1000000000000"); write(token,"/products",null,input).andExpect(status().isBadRequest());
        input.put("supplyPrice","0"); input.put("productId",UUID.randomUUID().toString());
        write(token,"/products",null,input).andExpect(status().isBadRequest()); input.put("productId",sku[0]);
        jdbc.update("UPDATE product_variant SET lifecycle_status='DISABLED' WHERE id=?",sku[1]);
        write(token,"/products",null,input).andExpect(status().isConflict());
        jdbc.update("UPDATE product_variant SET lifecycle_status='ACTIVE' WHERE id=?",sku[1]);
        var saved=body(write(token,"/products",null,input).andExpect(status().isOk()));
        input.put("version",0); input.put("customerId",createCustomer(token));
        write(token,"/products",saved.path("id").asText(),input).andExpect(status().isBadRequest());
        input.put("customerId",customer); jdbc.update("UPDATE customer SET status='DISABLED' WHERE id=?",customer);
        write(token,"/products",saved.path("id").asText(),input).andExpect(status().isConflict());
        input.put("status","DISABLED");
        write(token,"/products",saved.path("id").asText(),input).andExpect(status().isOk());
        input.put("version",1); input.put("status","ACTIVE");
        write(token,"/products",saved.path("id").asText(),input).andExpect(status().isConflict());
    }
    @Test void rejectsUnauthorizedAndInvalidRequests() throws Exception {
        mvc.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        String token=login("SELECTION");
        mvc.perform(get("/api/v1/customers").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customers/products").header("Authorization",token)).andExpect(status().isForbidden());
        write(token,"",null,customer("越权合成客户")).andExpect(status().isForbidden());
        write(token,"/products",null,quote("none",new String[]{"p","v"},"1")).andExpect(status().isForbidden());
        String admin=login("ADMIN");
        write(admin,"",null,customer("  ")).andExpect(status().isBadRequest());
        write(admin,"","missing",customer("合成客户")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/customers").param("page","-1").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/customers/products").param("size","101").header("Authorization",admin)).andExpect(status().isBadRequest());
    }
    @Test void maintainsMultipleTitlesWithValidationAndVersionProtection() throws Exception {
        String token=login("ADMIN");
        var input=customer("合成多抬头客户");
        var first=Map.of("invoiceTitle","合成甲","taxNumber","A");
        var second=Map.of("invoiceTitle","合成乙","taxNumber","B","bankAccount","SYN-BANK");
        input.put("invoiceTitles",List.of(first,second));
        var saved=body(write(token,"",null,input).andExpect(status().isOk())
            .andExpect(jsonPath("$.invoiceTitles.length()").value(2)).andExpect(jsonPath("$.invoiceTitle").value("合成甲")));
        String id=saved.path("id").asText();
        input.put("version",0);input.put("invoiceTitles",List.of(second));
        write(token,"",id,input).andExpect(status().isOk()).andExpect(jsonPath("$.invoiceTitles[0].invoiceTitle").value("合成乙"));
        write(token,"",id,input).andExpect(status().isConflict());
        input.put("version",1);input.put("invoiceTitles",List.of(first,first));
        write(token,"",id,input).andExpect(status().isBadRequest());
        input.put("invoiceTitles",List.of(Map.of("invoiceTitle"," ")));
        write(token,"",id,input).andExpect(status().isBadRequest());
        input.put("invoiceTitles",List.of(Map.of("invoiceTitle","x".repeat(201))));
        write(token,"",id,input).andExpect(status().isBadRequest());
        input.put("invoiceTitles",Collections.nCopies(51,first));
        write(token,"",id,input).andExpect(status().isBadRequest());
        input.put("invoiceTitles",List.of());
        write(token,"",id,input).andExpect(status().isOk()).andExpect(jsonPath("$.invoiceTitles.length()").value(0))
            .andExpect(jsonPath("$.invoiceTitle").value(""));
        var audit=jdbc.queryForObject("SELECT after_value FROM audit_event WHERE action='CUSTOMER_CREATE' AND resource_id=?",String.class,id);
        assertThat(audit).doesNotContain("SYN-BANK","合成甲","合成乙");
    }

    private String createCustomer(String token) throws Exception { return body(write(token,"",null,customer("合成客户"+UUID.randomUUID())).andExpect(status().isOk())).path("id").asText(); }
    private Map<String,Object> customer(String name) { return new HashMap<>(Map.of("name",name,"status","ACTIVE")); }
    private Map<String,Object> quote(String customer,String[] sku,String price) {
        return new HashMap<>(Map.of("customerId",customer,"productId",sku[0],"variantId",sku[1],"supplyPrice",price,
            "priceUnit","套","customerProductCode","合成客户编码","requirements","合成特殊要求","status","ACTIVE"));
    }
    private String[] sku() {
        String product=UUID.randomUUID().toString(), variant=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,'合成父体','ACTIVE','test','test')",product,product,product);
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?,'合成SKU蓝色','ACTIVE',?)",variant,product,variant,variant,variant);
        return new String[]{product,variant};
    }
    private ResultActions write(String token,String path,String id,Object input) throws Exception {
        var request=id==null?post("/api/v1/customers"+path):put("/api/v1/customers"+path+"/"+id);
        return mvc.perform(request.header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)));
    }
    private JsonNode body(ResultActions result) throws Exception { return mapper.readTree(result.andReturn().getResponse().getContentAsString()); }
    private String login(String role) throws Exception {
        String id=UUID.randomUUID().toString(); String password="Synthetic-Customer-42";
        jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",
            id,id,"Synthetic",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);
        return "Bearer "+body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk())).path("token").asText();
    }
}
