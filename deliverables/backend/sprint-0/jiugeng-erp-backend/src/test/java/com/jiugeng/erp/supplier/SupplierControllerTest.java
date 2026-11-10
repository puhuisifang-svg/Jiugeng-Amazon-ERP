package com.jiugeng.erp.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class SupplierControllerTest {
    private static final String PASSWORD = "Supplier-Test-42";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM purchase_receipt_line");
        jdbc.update("DELETE FROM purchase_receipt");
        jdbc.update("DELETE FROM purchase_order_line");
        jdbc.update("DELETE FROM purchase_order");
        jdbc.update("DELETE FROM product_variant_supplier");
        jdbc.update("DELETE FROM auxiliary_material_supplier");
        jdbc.update("DELETE FROM supplier");
        jdbc.update("DELETE FROM iam_password_reset_challenge");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
    }

    @Test
    void productMaterialRoleCanMaintainSuppliersWithOptimisticLockingAndSafeAudit() throws Exception {
        String token = createUserAndLogin("supplier-owner", "PRODUCT_MATERIAL");
        String createdBody = mvc.perform(post("/api/v1/suppliers")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(supplier("华南供应商", "13800138000", "https://detail.1688.com/offer/1.html", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(createdBody);
        String id = created.get("id").asText();

        mvc.perform(get("/api/v1/suppliers?q=华南&status=ACTIVE")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id));

        mvc.perform(put("/api/v1/suppliers/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(supplier("华南供应商升级", "13800138000", null, 0L))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/suppliers/{id}", id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(supplier("过期更新", "13800138000", null, 0L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPLIER_VERSION_CONFLICT"));

        String audit = jdbc.queryForObject(
                "SELECT after_value FROM audit_event WHERE action='SUPPLIER_CREATE' AND resource_id=?",
                String.class, id);
        assertThat(audit).contains("13****00").doesNotContain("13800138000");
    }

    @Test
    void validatesContactAnd1688UrlAndDeniesSelectionRole() throws Exception {
        String writer = createUserAndLogin("supplier-writer", "PRODUCT_MATERIAL");
        mvc.perform(post("/api/v1/suppliers").header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(supplier("无联系方式", null, null, null))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/suppliers").header("Authorization", bearer(writer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(supplier("错误站点", null, "https://example.com/item", null))))
                .andExpect(status().isBadRequest());

        String reader = createUserAndLogin("selector", "SELECTION");
        mvc.perform(get("/api/v1/suppliers").header("Authorization", bearer(reader)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/suppliers").header("Authorization", bearer(reader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(supplier("越权", "10086", null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void savesWechatContactAndHttpsQrUrlAndRejectsConflictingQrSources() throws Exception {
        String token = createUserAndLogin("supplier-wechat", "PRODUCT_MATERIAL");
        Map<String, Object> request = supplier("微信供应商", null, null, null);
        request.put("wechatId", "weixin_supplier_01");
        request.put("qrImageUrl", "https://images.example.com/wechat-qr.png");

        mvc.perform(post("/api/v1/suppliers").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wechatId").value("weixin_supplier_01"))
                .andExpect(jsonPath("$.qrImageUrl").value("https://images.example.com/wechat-qr.png"));

        mvc.perform(get("/api/v1/suppliers?q=weixin_supplier_01")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1));

        Map<String, Object> conflicting = supplier("冲突二维码", null, null, null);
        conflicting.put("wechatId", "conflict_supplier");
        conflicting.put("qrImageUrl", "https://images.example.com/wechat-qr.png");
        conflicting.put("qrImageId", UUID.randomUUID().toString());
        mvc.perform(post("/api/v1/suppliers").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(conflicting)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUPPLIER_FIELD_INVALID"));

        Map<String, Object> insecure = supplier("非安全二维码", null, null, null);
        insecure.put("wechatId", "insecure_supplier");
        insecure.put("qrImageUrl", "http://images.example.com/wechat-qr.png");
        mvc.perform(post("/api/v1/suppliers").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(insecure)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void materialMayHaveManySuppliersButOnlyOnePrimary() throws Exception {
        String token = createUserAndLogin("supplier-relations", "PRODUCT_MATERIAL");
        String first = createSupplier(token, "供应商甲", "10001");
        String second = createSupplier(token, "供应商乙", "10002");
        String materialId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO auxiliary_material
                    (id, material_code, material_code_normalized, name, material_type, unit, status, created_by, updated_by)
                VALUES (?, 'MAT-SUP', 'MAT-SUP', '供应商关系辅料', 'PACKAGING', '个', 'ACTIVE', 'test', 'test')
                """, materialId);

        mvc.perform(put("/api/v1/suppliers/material/{id}", materialId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("links", List.of(
                                Map.of("supplierId", first, "primary", true),
                                Map.of("supplierId", second, "primary", false))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].primary").value(true));

        mvc.perform(put("/api/v1/suppliers/material/{id}", materialId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("links", List.of(
                                Map.of("supplierId", first, "primary", true),
                                Map.of("supplierId", second, "primary", true))))))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM auxiliary_material_supplier WHERE auxiliary_material_id=?",
                Integer.class, materialId)).isEqualTo(2);
    }

    @Test
    void deletesUnlinkedSupplierWithVersionPermissionAndAuditChecks() throws Exception {
        String writer=createUserAndLogin("delete-writer","PRODUCT_MATERIAL");
        String reader=createUserAndLogin("delete-reader","SELECTION");
        String id=createSupplier(writer,"可删除合成供应商","10001");
        mvc.perform(delete("/api/v1/suppliers/{id}?version=0",id).header("Authorization",bearer(reader))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/suppliers/{id}?version=1",id).header("Authorization",bearer(writer))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/suppliers/{id}?version=-1",id).header("Authorization",bearer(writer))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/suppliers/{id}?version=0",id).header("Authorization",bearer(writer))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/suppliers/{id}",id).header("Authorization",bearer(writer))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/suppliers/{id}?version=0",id).header("Authorization",bearer(writer))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='SUPPLIER_DELETE' AND resource_id=?",Integer.class,id)).isEqualTo(1);
    }

    @Test
    void refusesDeletionOfSupplierReferencedByMaterial() throws Exception {
        String writer=createUserAndLogin("linked-delete-writer","PRODUCT_MATERIAL");
        String id=createSupplier(writer,"有关联合成供应商","10002");
        String materialId=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO auxiliary_material(id,material_code,material_code_normalized,name,material_type,unit,status,created_by,updated_by) VALUES(?,?,?,'合成辅料','PACKAGING','个','ACTIVE','test','test')",materialId,materialId,materialId);
        mvc.perform(put("/api/v1/suppliers/material/{id}",materialId).header("Authorization",bearer(writer)).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("links",List.of(Map.of("supplierId",id,"primary",true)))))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/suppliers/{id}/dependencies",id).header("Authorization",bearer(writer))).andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("MATERIAL")).andExpect(jsonPath("$[0].id").value(materialId)).andExpect(jsonPath("$[0].name").value("合成辅料"));
        mvc.perform(delete("/api/v1/suppliers/{id}?version=0",id).header("Authorization",bearer(writer))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPLIER_IN_USE"));
        mvc.perform(get("/api/v1/suppliers/{id}",id).header("Authorization",bearer(writer))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='SUPPLIER_DELETE' AND resource_id=?",Integer.class,id)).isZero();
    }

    @Test void dependencyListIncludesSkuAndHistoricalPurchaseAndRefreshesAfterUnlink() throws Exception {
        String token=createUserAndLogin("dependency-writer","PRODUCT_MATERIAL"), denied=createUserAndLogin("dependency-denied","SELECTION");
        String id=createSupplier(token,"合成关联供应商","SYN-01"),p=UUID.randomUUID().toString(),v=UUID.randomUUID().toString(),order=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,'合成父体','ACTIVE','test','test')",p,p,p);
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?,'合成SKU','ACTIVE',?)",v,p,v,v,v);
        jdbc.update("INSERT INTO product_variant_supplier(id,variant_id,supplier_id,created_by,updated_by) VALUES(?,?,?,'test','test')",UUID.randomUUID().toString(),v,id);
        jdbc.update("INSERT INTO purchase_order(id,order_no,order_type,supplier_id,status,currency,created_by,updated_by) VALUES(?,'SYN-DEPENDENCY','PRODUCT',?,'CANCELLED','CNY','test','test')",order,id);
        mvc.perform(get("/api/v1/suppliers/{id}/dependencies",id).header("Authorization",bearer(denied))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/suppliers/{id}/dependencies",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].type").value("PRODUCT")).andExpect(jsonPath("$[0].parentId").value(p)).andExpect(jsonPath("$[1].type").value("PURCHASE"));
        mvc.perform(delete("/api/v1/suppliers/{id}/archived-variants/{variant}?version=0",id,v).header("Authorization",bearer(token))).andExpect(status().isConflict());
        jdbc.update("UPDATE product SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",p);
        mvc.perform(get("/api/v1/suppliers/{id}/dependencies",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].archived").value(true));
        mvc.perform(delete("/api/v1/suppliers/{id}/archived-variants/{variant}?version=0",id,v).header("Authorization",bearer(denied))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/suppliers/{id}/archived-variants/{variant}?version=1",id,v).header("Authorization",bearer(token))).andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/suppliers/{id}/archived-variants/{variant}?version=0",id,v).header("Authorization",bearer(token))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE id=?",Integer.class,p)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='SUPPLIER_ARCHIVED_LINK_REMOVE' AND resource_id=?",Integer.class,id)).isEqualTo(1);
        mvc.perform(get("/api/v1/suppliers/{id}/dependencies",id).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/v1/suppliers/{id}?version=0",id).header("Authorization",bearer(token))).andExpect(status().isConflict());
    }

    private String createSupplier(String token, String name, String phone) throws Exception {
        String body = mvc.perform(post("/api/v1/suppliers").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(supplier(name, phone, null, null))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private Map<String, Object> supplier(String name, String phone, String sourceUrl, Long version) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("name", name);
        value.put("phone", phone);
        value.put("sourceUrl", sourceUrl);
        value.put("status", "ACTIVE");
        value.put("version", version);
        return value;
    }

    private String createUserAndLogin(String loginName, String role) throws Exception {
        String id = UUID.randomUUID().toString();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        jdbc.update("""
                INSERT INTO iam_user
                    (id, login_name, display_name, password_hash, role, status, must_change_password)
                VALUES (?, ?, ?, ?, ?, 'ENABLED', FALSE)
                """, id, loginName, loginName, encoder.encode(PASSWORD), role);jdbc.update("INSERT INTO iam_user_role(user_id,role_id) SELECT id,role FROM iam_user WHERE id=?",id);
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginName", loginName, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
}
