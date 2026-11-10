package com.jiugeng.erp.iam;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RoleControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired IamService iam;
    private static final String PASSWORD="Synthetic-Role-Test-42!";
    String admin, userId;
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM iam_session");jdbc.update("DELETE FROM iam_user");jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_role_permission WHERE role_id IN (SELECT id FROM iam_role WHERE kind='CUSTOM')");
        jdbc.update("DELETE FROM iam_role WHERE kind='CUSTOM'");
        iam.createUser("rbac-admin","Synthetic admin",PASSWORD,IamTypes.Role.ADMIN);
        userId=iam.createUser("rbac-user","Synthetic user",PASSWORD,IamTypes.Role.SELECTION).id();
        jdbc.update("UPDATE iam_user SET must_change_password=FALSE");admin=login("rbac-admin");
    }
    String login(String name) throws Exception { return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",name,"password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText(); }
    JsonNode create(String name) throws Exception { return json.readTree(mvc.perform(post("/api/v1/roles").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name",name,"description","Synthetic role")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    Map<String,Object> edit(JsonNode role,List<String> permissions,String status) { return Map.of("name",role.path("name").asText(),"description","Synthetic role","status",status,"permissions",permissions,"version",role.path("version").asLong(),"reason","Synthetic verification"); }
    JsonNode save(JsonNode role,List<String> permissions) throws Exception { ((com.fasterxml.jackson.databind.node.ObjectNode)role).put("version",jdbc.queryForObject("SELECT version FROM iam_role WHERE id=?",Long.class,role.path("id").asText())); return json.readTree(mvc.perform(put("/api/v1/roles/"+role.path("id").asText()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(role,permissions,"ENABLED")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    void assign(String... ids) throws Exception { long v=jdbc.queryForObject("SELECT authorization_version FROM iam_user WHERE id=?",Long.class,userId);mvc.perform(put("/api/v1/users/"+userId+"/roles").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("roleIds",List.of(ids),"version",v,"reason","")))).andExpect(status().isOk()); }
    @Test void newRoleIsEnabledWithoutImplicitPermissionsAndDraftIsRejected() throws Exception {
        var role=create("默认启用角色");
        assertThat(role.path("status").asText()).isEqualTo("ENABLED");
        assertThat(role.path("permissions").size()).isZero();
        role=save(role,List.of());
        mvc.perform(put("/api/v1/roles/"+role.path("id").asText()).header("Authorization",admin)
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(role,List.of(),"DRAFT"))))
            .andExpect(status().isBadRequest());
    }
    @Test void removingLastRolePreservesStatusRevokesSessionAndAllowsRoleDeletion() throws Exception {
        var role=save(create("待移除角色"),List.of("products.read")); String id=role.path("id").asText(); assign(id);
        String old=login("rbac-user");
        long v=jdbc.queryForObject("SELECT authorization_version FROM iam_user WHERE id=?",Long.class,userId);
        mvc.perform(post("/api/v1/users/"+userId+"/roles/preview").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("roleIds",List.of(),"version",v,"reason",""))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.removed").isArray());
        assign();
        assertThat(jdbc.queryForObject("SELECT status FROM iam_user WHERE id=?",String.class,userId)).isEqualTo("ENABLED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user_role WHERE user_id=?",Integer.class,userId)).isZero();
        mvc.perform(get("/api/v1/auth/me").header("Authorization",old)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='ACCOUNT_DISABLE'",Integer.class)).isZero();
        long version=jdbc.queryForObject("SELECT version FROM iam_role WHERE id=?",Long.class,id);
        mvc.perform(delete("/api/v1/roles/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version",version,"reason","Synthetic deletion")))).andExpect(status().isOk());
        String noRole=login("rbac-user");
        mvc.perform(get("/api/v1/auth/me").header("Authorization",noRole)).andExpect(status().isOk()).andExpect(jsonPath("$.capabilities").isEmpty());
        mvc.perform(get("/api/v1/audit/events").header("Authorization",noRole)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/products").header("Authorization",noRole)).andExpect(status().isForbidden());
        assign("SELECTION");
        assertThat(jdbc.queryForObject("SELECT status FROM iam_user WHERE id=?",String.class,userId)).isEqualTo("ENABLED");
        login("rbac-user");
    }
    @Test void roleAssignmentNeverChangesDisabledAccountStatusAndNoRoleCanBeEnabled() throws Exception {
        mvc.perform(post("/api/v1/users/"+userId+"/disable").header("Authorization",admin)).andExpect(status().isOk());
        assign();
        assertThat(jdbc.queryForObject("SELECT status FROM iam_user WHERE id=?",String.class,userId)).isEqualTo("DISABLED");
        assign("SELECTION");
        assertThat(jdbc.queryForObject("SELECT status FROM iam_user WHERE id=?",String.class,userId)).isEqualTo("DISABLED");
        assign();
        mvc.perform(post("/api/v1/users/"+userId+"/enable").header("Authorization",admin)).andExpect(status().isOk());
        login("rbac-user");
    }
    @Test void selfAuditRequiresExplicitRoleGrantAndCanBeRevoked() throws Exception {
        assertThat(PermissionCatalog.ALL.stream().filter(p->p.code().equals("audit.read.self")).findFirst().orElseThrow().assignable()).isTrue();
        var role=save(create("审计权限测试"),List.of("products.read"));
        assign(role.path("id").asText());
        String token=login("rbac-user");
        mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.capabilities[?(@ == 'audit.read.self')]").isEmpty());
        mvc.perform(get("/api/v1/audit/events").header("Authorization",token)).andExpect(status().isForbidden());
        role=save(role,List.of("products.read","audit.read.self"));
        token=login("rbac-user");
        mvc.perform(get("/api/v1/audit/events").header("Authorization",token)).andExpect(status().isOk());
        role=save(role,List.of("products.read"));
        mvc.perform(get("/api/v1/audit/events").header("Authorization",token)).andExpect(status().isUnauthorized());
        token=login("rbac-user");
        mvc.perform(get("/api/v1/audit/events").header("Authorization",token)).andExpect(status().isForbidden());
        save(role,List.of("audit.read.self"));
        token=login("rbac-user");
        mvc.perform(get("/api/v1/audit/events").header("Authorization",token)).andExpect(status().isOk());
    }
    @Test void multipleRolesUnionDependenciesAndSessionRevocation() throws Exception {
        var product=save(create("只读产品"),List.of("products.read"));var finance=save(create("账单维护"),List.of("finance.write"));
        String old=login("rbac-user");assign(product.path("id").asText(),finance.path("id").asText());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",old)).andExpect(status().isUnauthorized());
        String token=login("rbac-user");String body=mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("products.read","finance.read","finance.write").doesNotContain("system.users.manage","costs.read");
        mvc.perform(delete("/api/v1/products/nonexistent?version=0").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/product-monitoring").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/"+userId+"/permission-sources").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$['finance.write'][0].name").value("账单维护"));
        save(finance,List.of("finance.read"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isUnauthorized());
    }
    @Test void rejectsPrivilegeEscalationAndProtectedRoleChanges() throws Exception {
        var role=create("测试岗位");
        mvc.perform(put("/api/v1/roles/"+role.path("id").asText()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(role,List.of("system.users.manage"),"ENABLED")))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/roles/ADMIN").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"Synthetic test\"}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action='AUTHORIZATION_CHANGE_FAILED'",Integer.class)).isEqualTo(2);
    }
    @Test void allowsDisablingLastRoleButStillProtectsRoleWithMembers() throws Exception {
        var role=save(create("单一职责"),List.of("products.read"));String id=role.path("id").asText();assign(id);((com.fasterxml.jackson.databind.node.ObjectNode)role).put("version",jdbc.queryForObject("SELECT version FROM iam_role WHERE id=?",Long.class,id));
        mvc.perform(put("/api/v1/roles/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(role,List.of("products.read"),"DISABLED")))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM iam_role WHERE id=?",String.class,id)).isEqualTo("DISABLED");
        mvc.perform(delete("/api/v1/roles/"+id).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version",role.path("version").asLong()+1,"reason","Synthetic delete")))).andExpect(status().isConflict());
    }
    @Test void staleVersionDoesNotOverwriteAndNoOpPermissionsKeepsSession() throws Exception {
        var draft=create("版本检查");var role=save(draft,List.of("products.read"));assign(role.path("id").asText());String token=login("rbac-user");
        mvc.perform(put("/api/v1/roles/"+role.path("id").asText()).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit(draft,List.of("products.write"),"ENABLED")))).andExpect(status().isConflict());
        save(role,List.of("products.read"));mvc.perform(get("/api/v1/auth/me").header("Authorization",token)).andExpect(status().isOk());
    }
    @Test void creationWithDisabledRoleIsAtomicAndAdminCannotMixRoles() throws Exception {
        var draft=create("停用角色");jdbc.update("UPDATE iam_role SET status='DISABLED' WHERE id=?",draft.path("id").asText());int before=jdbc.queryForObject("SELECT COUNT(*) FROM iam_user",Integer.class);
        mvc.perform(post("/api/v1/users/with-roles").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName","synthetic-new","displayName","Synthetic new","initialPassword",PASSWORD,"roleIds",List.of(draft.path("id").asText()))))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user",Integer.class)).isEqualTo(before);
        mvc.perform(put("/api/v1/users/"+userId+"/roles").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"roleIds\":[\"ADMIN\",\"SELECTION\"],\"version\":1,\"reason\":\"Synthetic mix\"}")).andExpect(status().isBadRequest());
    }

    @Test void migrationPreservesLegacyCapabilitiesAndAddsOnlyExplicitSplits() {
        for(var role:List.of(IamTypes.Role.ADMIN,IamTypes.Role.PRODUCT_MATERIAL,IamTypes.Role.SELECTION)) {
            var actual=new TreeSet<>(jdbc.queryForList("SELECT permission_code FROM iam_role_permission WHERE role_id=?",String.class,role.name()));
            var expected=new TreeSet<>(IamTypes.capabilities(role));
            expected.addAll(List.of("monitoring.read","monitoring.write","monitoring.collect","monitoring.remove","products.export"));
            if(expected.contains("products.write")) expected.addAll(List.of("products.delete","samples.read","samples.write","samples.remove","development.read","development.write"));
            else if(expected.contains("products.read")) expected.addAll(List.of("samples.read","development.read"));
            if(expected.contains("customers.write")) expected.addAll(List.of("customers.delete","orders.read","orders.write","orders.transition","invoices.read","invoices.write"));
            if(expected.contains("suppliers.write")) expected.add("suppliers.delete");
            if(expected.contains("watch.write")) expected.addAll(List.of("watch.collect","watch.remove"));
            if(expected.contains("purchases.write")) expected.addAll(List.of("purchases.submit","purchases.cancel"));
            if(expected.contains("inventory.write")) expected.addAll(List.of("inventory.inbound","inventory.outbound","inventory.reverse"));
            if(expected.contains("costs.write")) expected.add("costs.delete");
            if(expected.contains("finance.write")) expected.addAll(List.of("finance.void","finance.attachment.remove"));
            if(expected.contains("finance.read")) expected.add("finance.export");
            if(role==IamTypes.Role.ADMIN) expected.addAll(List.of("forwarders.read","forwarders.write","forwarders.export","forwarders.delete","selection.review","development.review","factory.employees.read","factory.employees.write","factory.rates.read","factory.rates.write","factory.reports.read","factory.reports.write","factory.reports.review","factory.payroll.read","factory.payroll.write","factory.payroll.lock","factory.payroll.correct","factory.payroll.pay","factory.payroll.export","factory.expenses.read","factory.expenses.write","factory.expenses.remove"));
            assertThat(actual).as(role.name()).isEqualTo(expected);
        }
    }
    @Test void simultaneousAdminsCannotDisableEachOther() throws Exception {
        String adminId=jdbc.queryForObject("SELECT id FROM iam_user WHERE login_name='rbac-admin'",String.class);
        String otherId=iam.createUser("rbac-other-admin","Synthetic other admin",PASSWORD,IamTypes.Role.ADMIN).id();
        jdbc.update("UPDATE iam_user SET must_change_password=FALSE WHERE id=?",otherId);String other=login("rbac-other-admin");
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var barrier=new java.util.concurrent.CyclicBarrier(2);
            var one=pool.submit(()->{barrier.await();return mvc.perform(post("/api/v1/users/"+otherId+"/disable").header("Authorization",admin)).andReturn().getResponse().getStatus();});
            var two=pool.submit(()->{barrier.await();return mvc.perform(post("/api/v1/users/"+adminId+"/disable").header("Authorization",other)).andReturn().getResponse().getStatus();});
            assertThat(List.of(one.get(),two.get())).contains(200).anyMatch(code->code==403 || code==401 || code==409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_user WHERE role='ADMIN' AND status='ENABLED' AND locked_at IS NULL",Integer.class)).isEqualTo(1);
    }
}
