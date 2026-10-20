package com.jiugeng.erp.catalogdeletion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CatalogDeletionControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.jiugeng.erp.customer.CustomerProductLookup customerProducts;
    @Autowired com.jiugeng.erp.product.ProductImportGateway imports;
    @Autowired com.jiugeng.erp.product.ProductLookup catalog;
    record Fixture(String product,String variant,String customer,String cp) {}
    Fixture fixture() {
        String p=UUID.randomUUID().toString(),v=UUID.randomUUID().toString(),c=UUID.randomUUID().toString(),cp=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,'合成父体','ACTIVE','test','test')",p,p,p);
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?,'合成子体','ACTIVE',?)",v,p,v,v,v);
        jdbc.update("INSERT INTO customer(id,name,status,created_by,updated_by) VALUES(?,'合成客户','ACTIVE','test','test')",c);
        jdbc.update("INSERT INTO customer_product(id,customer_id,product_id,variant_id,sku_code,product_name,price_unit,status,created_by,updated_by) VALUES(?,?,?,?,?,'合成子体','套','ACTIVE','test','test')",cp,c,p,v,v);
        return new Fixture(p,v,c,cp);
    }
    long count(String table,String id) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=?",Long.class,id); }
    String productPath(Fixture f) { return "/api/v1/products/"+f.product(); }
    String cpPath(Fixture f) { return "/api/v1/customers/products/"+f.cp(); }
    @Test void deletedCatalogCannotBeSelectedOrRevivedByImport() throws Exception {
        var f=fixture();String token=login("ADMIN");
        jdbc.update("UPDATE product SET product_code_normalized=UPPER(product_code) WHERE id=?",f.product());
        jdbc.update("UPDATE product_variant SET sku_code_normalized=UPPER(sku_code) WHERE id=?",f.variant());
        mvc.perform(delete(productPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(catalog.findSku(f.product(),f.variant())).isEmpty();
        assertThat(catalog.findExistingSku(f.product(),f.variant())).isPresent();
        var parent=new com.jiugeng.erp.product.ProductImportGateway.ImportRow("导入模板",2,
            Map.of("product_code",f.product(),"product_name","合成","status","ACTIVE"));
        assertThat(imports.validateProducts(List.of(parent)).errors()).anyMatch(e->e.code().equals("PRODUCT_DELETED"));
        var sku=new com.jiugeng.erp.product.ProductImportGateway.ImportRow("variants_导入模板",2,
            Map.of("product_code",f.product(),"sku_code",f.variant(),"variant_name","合成","status","ACTIVE"));
        assertThat(imports.validateVariants(List.of(sku)).errors()).anyMatch(e->e.code().equals("SKU_DELETED"));
        mvc.perform(delete(productPath(f)).param("version","1").header("Authorization",token)).andExpect(status().isNotFound());
    }
    @Test void parentDeletionRetainsCustomerProductsMediaDocumentsAndAudit() throws Exception {
        var f=fixture();String token=login("ADMIN");
        String parentImage=image("PRODUCT_MAIN","PRODUCT",f.product()),skuImage=image("SKU_MAIN","PRODUCT_VARIANT",f.variant());
        jdbc.update("UPDATE product SET main_image_asset_id=? WHERE id=?",parentImage,f.product());
        jdbc.update("UPDATE product_variant SET main_image_asset_id=? WHERE id=?",skuImage,f.variant());
        jdbc.update("INSERT INTO variant_attribute(variant_id,attribute_code,attribute_code_normalized,attribute_value,display_order) VALUES(?,'COLOR','COLOR','synthetic',0)",f.variant());
        jdbc.update("INSERT INTO sku_document(id,variant_id,kind,file_name,byte_size,content,uploaded_by) VALUES(?,?,'MANUAL','synthetic.txt',1,?,'test')",UUID.randomUUID().toString(),f.variant(),new byte[]{1});
        mvc.perform(delete(productPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM catalog_image_asset WHERE id=?",String.class,parentImage)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT status FROM catalog_image_asset WHERE id=?",String.class,skuImage)).isEqualTo("ACTIVE");
        assertThat(count("product",f.product())).isOne();assertThat(count("product_variant",f.variant())).isOne();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM product WHERE id=?",java.sql.Timestamp.class,f.product())).isNotNull();
        mvc.perform(get(productPath(f)).header("Authorization",token)).andExpect(status().isNotFound());
        assertThat(customerProducts.active(f.cp())).isEmpty();
        assertThat(customerProducts.forExisting(f.cp())).isPresent();
        mvc.perform(get("/api/v1/products").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '"+f.product()+"')]").isEmpty());
        assertThat(count("customer_product",f.cp())).isOne();assertThat(count("customer",f.customer())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='PRODUCT_DELETE' AND resource_id=?",Long.class,f.product())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sku_document WHERE variant_id=?",Long.class,f.variant())).isOne();
    }
    @Test void skuDeletionKeepsParentAndOtherSkuAndIncrementsParentVersion() throws Exception {
        var f=fixture();String token=login("PRODUCT_MATERIAL"),other=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?,'其他子体','ACTIVE',?)",other,f.product(),other,other,other);
        mvc.perform(delete(productPath(f)+"/skus/"+f.variant()).param("productVersion","0").param("version","0").header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(count("product",f.product())).isOne();assertThat(count("product_variant",other)).isOne();
        assertThat(count("product_variant",f.variant())).isOne();
        assertThat(jdbc.queryForObject("SELECT deleted_at FROM product_variant WHERE id=?",java.sql.Timestamp.class,f.variant())).isNotNull();assertThat(count("customer_product",f.cp())).isOne();
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE id=?",Long.class,f.product())).isEqualTo(1);
    }
    @Test void standaloneCustomerProductDeletePreservesSourceAndRejectsStaleVersion() throws Exception {
        var f=fixture();String token=login("ADMIN");
        mvc.perform(delete(cpPath(f)).param("version","1").header("Authorization",token)).andExpect(status().isConflict());
        mvc.perform(delete(cpPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(count("product",f.product())).isOne();assertThat(count("product_variant",f.variant())).isOne();
        assertThat(count("customer_product",f.cp())).isOne();
        assertThat(customerProducts.active(f.cp())).isEmpty();
        assertThat(customerProducts.forExisting(f.cp())).isPresent();
        assertThat(jdbc.queryForObject("SELECT deleted_by FROM customer_product WHERE id=?",String.class,f.cp())).isNotBlank();
        mvc.perform(get("/api/v1/customers/products").param("customerId",f.customer()).header("Authorization",token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        // Re-registering the same customer/SKU gets a distinct identity; the historical row stays deleted.
        var input=Map.of("customerId",f.customer(),"productId",f.product(),"variantId",f.variant(),
            "supplyPrice",12,"priceUnit","套","status","ACTIVE");
        mvc.perform(post("/api/v1/customers/products").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(input))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/customers/products").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(input))).andExpect(status().isConflict());
        mvc.perform(delete(cpPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNotFound());
    }
    @Test void invoiceIsRetainedAfterParentLogicalDeletion() throws Exception {
        var f=fixture();String token=login("ADMIN");
        jdbc.update("INSERT INTO customer_invoice(id,customer_product_id,customer_id,customer_name,invoice_title,item_name,unit,quantity,net_unit_price,net_amount,tax_amount,total_amount,created_by,updated_by) VALUES(?,?,?,'合成客户','合成抬头','合成品','套',1,1,1,0,1,'test','test')",UUID.randomUUID().toString(),f.cp(),f.customer());
        mvc.perform(delete(productPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_invoice WHERE customer_product_id=?",Long.class,f.cp())).isOne();
        mvc.perform(delete(productPath(f)+"/skus/"+f.variant()).param("productVersion","0").param("version","0").header("Authorization",token)).andExpect(status().isNotFound());
        mvc.perform(delete(cpPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNotFound());
        assertThat(count("product",f.product())).isOne();assertThat(count("product_variant",f.variant())).isOne();assertThat(count("customer_product",f.cp())).isOne();
    }
    @Test void archivedShipmentsAreRetainedAfterDeletion() throws Exception {
        var f=fixture();String token=login("ADMIN");
        jdbc.update("INSERT INTO dashboard_today_shipment(id,ship_date,product_id,product_code_snapshot,product_name_snapshot,quantity,box_count,latest_ship_at,status,created_by,updated_by,variant_id,customer_product_id) VALUES(?,CURRENT_DATE,?,'SYN','合成',1,1,CURRENT_TIMESTAMP,'ARCHIVED','test','test',?,?)",UUID.randomUUID().toString(),f.product(),f.variant(),f.cp());
        mvc.perform(delete(productPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNoContent());
        mvc.perform(delete(cpPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNotFound());
        assertThat(count("customer_product",f.cp())).isOne();
    }
    @Test void cancelledPurchasesAreRetainedAfterDeletion() throws Exception {
        var f=fixture();String token=login("ADMIN"),supplier=UUID.randomUUID().toString(),order=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO supplier(id,name,phone,status,created_by,updated_by) VALUES(?,'合成供应商','SYN','ACTIVE','test','test')",supplier);
        jdbc.update("INSERT INTO purchase_order(id,order_no,order_type,supplier_id,status,currency,created_by,updated_by) VALUES(?,?,'PRODUCT',?,'CANCELLED','CNY','test','test')",order,order.substring(0,30),supplier);
        jdbc.update("INSERT INTO purchase_order_line(id,order_id,line_no,item_id,item_code,item_name,unit,ordered_quantity,unit_price) VALUES(?,?,1,?,'SYN','合成','套',1,1)",UUID.randomUUID().toString(),order,f.variant());
        mvc.perform(delete(productPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNoContent());
        mvc.perform(delete(cpPath(f)).param("version","0").header("Authorization",token)).andExpect(status().isNotFound());
    }
    @Test void permissionsVersionsAndOwnershipAreRequired() throws Exception {
        var f=fixture();String reader=login("SELECTION"),admin=login("ADMIN");
        mvc.perform(delete(productPath(f)).param("version","0")).andExpect(status().isUnauthorized());
        mvc.perform(delete(productPath(f)).param("version","0").header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(delete(cpPath(f)).param("version","0").header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(delete(productPath(f)).header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(delete(productPath(f)).param("version","-1").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(delete(productPath(f)).param("version","1").header("Authorization",admin)).andExpect(status().isConflict());
        mvc.perform(delete(productPath(f)+"/skus/"+UUID.randomUUID()).param("productVersion","0").param("version","0").header("Authorization",admin)).andExpect(status().isNotFound());
        assertThat(count("product",f.product())).isOne();
    }
    String image(String purpose,String type,String resource) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO catalog_image_asset(id,purpose,status,original_storage_key,thumbnail_storage_key,source_file_name,source_media_type,stored_media_type,byte_size,sha256,pixel_width,pixel_height,uploaded_by,attached_resource_type,attached_resource_id) VALUES(?,?,'ACTIVE',?,?,'synthetic.png','image/png','image/png',1,?,1,1,'test',?,?)",id,purpose,id+"-original.png",id+"-thumbnail.png","0".repeat(64),type,resource);
        return id;
    }
    String login(String role) throws Exception {
        String id=UUID.randomUUID().toString(),password="Synthetic-Delete-42";
        jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);
        return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();
    }
}
