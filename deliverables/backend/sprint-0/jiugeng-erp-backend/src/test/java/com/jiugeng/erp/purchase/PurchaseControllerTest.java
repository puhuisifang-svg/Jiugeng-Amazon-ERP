package com.jiugeng.erp.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PurchaseControllerTest {
    private static final String PASSWORD = "Purchase-Test-42";
    @Autowired MockMvc mvc; @Autowired ObjectMapper objectMapper; @Autowired JdbcTemplate jdbc;
    String supplierId; String materialId; String variantId;

    @BeforeEach void setup() {
        cleanupPurchases();
        supplierId=UUID.randomUUID().toString(); materialId=UUID.randomUUID().toString(); variantId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO supplier(id,name,phone,status,created_by,updated_by) VALUES(?,?,'10086','ACTIVE','test','test')",supplierId,"采购供应商");
        String suffix=materialId.substring(0,8).toUpperCase(); String materialCode="PUR-MAT-"+suffix;
        jdbc.update("INSERT INTO auxiliary_material(id,material_code,material_code_normalized,name,material_type,unit,status,created_by,updated_by) VALUES(?,?,?,?, '包装','PCS','ACTIVE','test','test')",materialId,materialCode,materialCode,"采购辅料");
        String productId=UUID.randomUUID().toString();
        String productCode="PUR-PROD-"+suffix,skuCode="PUR-SKU-"+suffix;
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,?, 'ACTIVE','test','test')",productId,productCode,productCode,"采购产品");
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?, '采购SKU','ACTIVE',?)",variantId,productId,skuCode,skuCode,"a".repeat(64));
        jdbc.update("INSERT INTO product_variant_supplier(id,variant_id,supplier_id,primary_slot,created_by,updated_by) VALUES(?,?,?,'PRIMARY','test','test')",UUID.randomUUID().toString(),variantId,supplierId);
        jdbc.update("INSERT INTO auxiliary_material_supplier(id,auxiliary_material_id,supplier_id,primary_slot,created_by,updated_by) VALUES(?,?,?,'PRIMARY','test','test')",UUID.randomUUID().toString(),materialId,supplierId);
    }
    @AfterEach void cleanup(){cleanupPurchases();}

    @Test void materialReceiptPostsInventoryInSameWorkflowAndIsIdempotent() throws Exception {
        String token=login("buyer","PRODUCT_MATERIAL");
        JsonNode order=createOrder(token,"MATERIAL",materialId,"10", "2.5"); String id=order.get("id").asText(); long version=order.get("version").asLong();
        mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",version))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        String lineId=order.at("/lines/0/id").asText(); Map<String,Object> receipt=Map.of("requestId","RECEIPT-001","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",lineId,"quantity",4)));
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(false)).andExpect(jsonPath("$.lines[0].inventoryTransactionId").isNotEmpty());
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(receipt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.replayed").value(true));
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_balance WHERE auxiliary_material_id=?",java.math.BigDecimal.class,materialId)).isEqualByComparingTo("4.0000");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE request_id LIKE 'PURCHASE:%'",Integer.class)).isEqualTo(1);
    }

    @Test void productReceiptCompletesWithoutInventorySideEffectAndOverReceiptIsRejected() throws Exception {
        String token=login("product-buyer","PRODUCT_MATERIAL"); JsonNode order=createOrder(token,"PRODUCT",variantId,"2","18"); String id=order.get("id").asText();
        String submitted=mvc.perform(post("/api/v1/purchases/orders/{id}/submit",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",order.get("version").asLong())))).andReturn().getResponse().getContentAsString();
        String lineId=order.at("/lines/0/id").asText();
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId","PRODUCT-OVER","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",lineId,"quantity",3))))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PURCHASE_OVER_RECEIPT"));
        mvc.perform(post("/api/v1/purchases/orders/{id}/receipts",id).header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId","PRODUCT-OK","receivedAt","2026-10-16T08:00:00Z","lines",List.of(Map.of("orderLineId",lineId,"quantity",2))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].inventoryTransactionId").doesNotExist());
        mvc.perform(get("/api/v1/purchases/orders/{id}",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction WHERE request_id LIKE 'PURCHASE:%'",Integer.class)).isZero();
        assertThat(objectMapper.readTree(submitted).get("status").asText()).isEqualTo("SUBMITTED");
    }

    @Test void selectionRoleCannotReadOrWritePurchases() throws Exception {
        String token=login("selector-purchase","SELECTION");
        mvc.perform(get("/api/v1/purchases/orders").header("Authorization",bearer(token))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void referencesOnlyExposeItemsLinkedToTheSelectedSupplierAndDetailIncludesHistory() throws Exception {
        String token=login("reference-buyer","PRODUCT_MATERIAL");
        mvc.perform(get("/api/v1/purchases/references").param("orderType","PRODUCT").param("supplierId",supplierId)
                .header("Authorization",bearer(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(variantId)).andExpect(jsonPath("$[0].primarySupplier").value(true));
        JsonNode order=createOrder(token,"PRODUCT",variantId,"2","18");
        mvc.perform(get("/api/v1/purchases/orders/{id}/detail",order.get("id").asText())
                .header("Authorization",bearer(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.orderNo").value(order.get("orderNo").asText()))
                .andExpect(jsonPath("$.receipts").isArray()).andExpect(jsonPath("$.history[0].action").value("PURCHASE_ORDER_CREATE"));
    }

    @Test void orderCannotUseAnItemThatIsNotLinkedToItsSupplier() throws Exception {
        String token=login("supplier-link-buyer","PRODUCT_MATERIAL");
        String otherSupplier=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO supplier(id,name,phone,status,created_by,updated_by) VALUES(?,?,'10010','ACTIVE','test','test')",otherSupplier,"无关联供应商");
        mvc.perform(get("/api/v1/purchases/references").param("orderType","MATERIAL").param("supplierId",otherSupplier)
                .header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        Map<String,Object> request=Map.of("orderType","MATERIAL","supplierId",otherSupplier,"currency","CNY",
                "lines",List.of(Map.of("itemId",materialId,"quantity",1,"unitPrice",1)));
        mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLIER_ITEM_LINK_REQUIRED"));
    }

    private JsonNode createOrder(String token,String type,String itemId,String quantity,String price)throws Exception{
        Map<String,Object> request=Map.of("orderType",type,"supplierId",supplierId,"currency","CNY","lines",List.of(Map.of("itemId",itemId,"quantity",quantity,"unitPrice",price)));
        String body=mvc.perform(post("/api/v1/purchases/orders").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString();return objectMapper.readTree(body);
    }
    private String login(String name,String role)throws Exception{String uid=UUID.randomUUID().toString();var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",uid,name,name,encoder.encode(PASSWORD),role);String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginName",name,"password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return objectMapper.readTree(body).get("token").asText();}
    private void cleanupPurchases(){jdbc.update("DELETE FROM purchase_receipt_line");jdbc.update("DELETE FROM purchase_receipt");jdbc.update("DELETE FROM purchase_order_line");jdbc.update("DELETE FROM purchase_order");}
    private String json(Object value)throws Exception{return objectMapper.writeValueAsString(value);}private String bearer(String token){return "Bearer "+token;}
}
