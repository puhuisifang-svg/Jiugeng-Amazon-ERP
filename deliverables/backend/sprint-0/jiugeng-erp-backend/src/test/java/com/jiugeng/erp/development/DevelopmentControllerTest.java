package com.jiugeng.erp.development;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class DevelopmentControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    String admin, selector, productUser;
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM product_development");
        jdbc.update("DELETE FROM watch_new_product_event");
        jdbc.update("DELETE FROM selection_candidate_evidence"); jdbc.update("DELETE FROM selection_candidate_note");
        jdbc.update("DELETE FROM selection_candidate_tag"); jdbc.update("DELETE FROM selection_candidate");
        admin=user("ADMIN"); selector=user("SELECTION"); productUser=user("PRODUCT_MATERIAL");
    }

    @Test void approvalCreatesOneDraftSnapshotAndDoesNotGrantSelectionWrite() throws Exception {
        String id=candidate();
        mvc.perform(get("/api/v1/product-development/"+id).header("Authorization",selector)).andExpect(status().isNotFound());
        approve(id);
        mvc.perform(get("/api/v1/product-development/"+id).header("Authorization",selector))
            .andExpect(status().isOk()).andExpect(jsonPath("$.item.status").value("DRAFT"))
            .andExpect(jsonPath("$.item.source.score").value(82)).andExpect(jsonPath("$.item.fields.productName").value("开发测试"))
            .andExpect(jsonPath("$.item.fields.purchaseCost").isEmpty()).andExpect(jsonPath("$.history.length()").value(1));
        mvc.perform(post("/api/v1/selection/candidates/"+id+"/transition").header("Authorization",selector)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_development WHERE candidate_id=?",Integer.class,id)).isEqualTo(1);
        mvc.perform(put("/api/v1/selection/candidates/"+id+"/evaluation").header("Authorization",selector)
            .contentType(MediaType.APPLICATION_JSON).content("{\"score\":50,\"category\":\"changed\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/product-development/"+id).header("Authorization",productUser))
            .andExpect(status().isOk()).andExpect(jsonPath("$.item.source.score").value(82));
        update(id,selector,0,fields(id)).andExpect(status().isForbidden());
        transition(id,selector,0,"VOID","test").andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/product-development")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/product-development").header("Authorization",admin).param("q","开发测试").param("status","DRAFT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/v1/product-development").header("Authorization",selector).param("size","1")).andExpect(status().isBadRequest());
    }

    @Test void validatesLifecycleVersionsAndProductOutcome() throws Exception {
        String id=candidate(); approve(id);
        transition(id,admin,0,"IN_PROGRESS",null).andExpect(status().isBadRequest());
        ObjectNode f=fields(id); f.put("developer","开发员").put("startDate","2026-10-16").put("endDate","2026-11-01");
        f.put("purchaseCost",-1); update(id,admin,0,f).andExpect(status().isBadRequest());
        f.put("purchaseCost",12.25).put("launchDate","2026-10-01"); update(id,admin,0,f).andExpect(status().isBadRequest());
        f.putNull("launchDate"); update(id,productUser,0,f).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        update(id,admin,0,f).andExpect(status().isConflict());
        transition(id,productUser,1,"IN_PROGRESS",null).andExpect(status().isOk());
        transition(id,productUser,2,"COMPLETED",null).andExpect(status().isBadRequest());
        String product=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,product_name,status,created_by,updated_by) VALUES (?,?,?,?,?,'ACTIVE','test','test')",product,product,product,"开发成果","开发成果");
        try {
            f.put("linkedProductId",product).put("sampleNotes","验收合格");
            update(id,productUser,2,f).andExpect(status().isOk());
            jdbc.update("UPDATE product SET status='DISABLED' WHERE id=?",product);
            transition(id,admin,3,"COMPLETED",null).andExpect(status().isBadRequest());
            jdbc.update("UPDATE product SET status='ACTIVE' WHERE id=?",product);
            transition(id,admin,3,"COMPLETED",null).andExpect(status().isOk());
            update(id,admin,4,f).andExpect(status().isConflict());
            transition(id,admin,4,"VOID","no").andExpect(status().isConflict());
            mvc.perform(get("/api/v1/product-development/"+id).header("Authorization",selector))
                .andExpect(jsonPath("$.product.id").value(product)).andExpect(jsonPath("$.item.status").value("COMPLETED"));
        } finally {jdbc.update("DELETE FROM product WHERE id=?",product);}
    }

    @Test void voidRequiresReasonAndIsTerminal() throws Exception {
        String id=candidate(); approve(id);
        transition(id,admin,0,"VOID"," ").andExpect(status().isBadRequest());
        transition(id,admin,0,"VOID","需求取消").andExpect(status().isOk()).andExpect(jsonPath("$.voidReason").value("需求取消"));
        transition(id,admin,1,"IN_PROGRESS",null).andExpect(status().isConflict());
        update(id,admin,1,fields(id)).andExpect(status().isConflict());
    }

    @Test void concurrentApprovalAndRejectionCannotCreateAnOrphanDevelopment() throws Exception {
        String id=candidate(); candidateTransition(id,"REVIEWING",200);
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks=java.util.List.of("APPROVED","REJECTED").stream().map(target -> (java.util.concurrent.Callable<Integer>) () ->
                mvc.perform(post("/api/v1/selection/candidates/"+id+"/transition").header("Authorization",selector)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(target.equals("REJECTED") ? Map.of("status",target,"rejectionReason","不采用") : Map.of("status",target))))
                .andReturn().getResponse().getStatus()).toList();
            var statuses=executor.invokeAll(tasks).stream().map(f -> {try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).sorted().toList();
            assertThat(statuses).containsExactly(200,409);
            String state=jdbc.queryForObject("SELECT status FROM selection_candidate WHERE id=?",String.class,id);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_development WHERE candidate_id=?",Integer.class,id)).isEqualTo(state.equals("APPROVED")?1:0);
        } finally {executor.shutdownNow();}
    }

    @Test void developmentInsertFailureRollsBackCandidateApprovalAndAudit() throws Exception {
        String id=candidate(); candidateTransition(id,"REVIEWING",200);
        jdbc.execute("ALTER TABLE product_development ADD CONSTRAINT test_deny_development CHECK (source_score < 0)");
        try {
            try {candidateTransition(id,"APPROVED",500);} catch(jakarta.servlet.ServletException expected) { /* unhandled persistence error */ }
            assertThat(jdbc.queryForObject("SELECT status FROM selection_candidate WHERE id=?",String.class,id)).isEqualTo("REVIEWING");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_development WHERE candidate_id=?",Integer.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='CANDIDATE_TRANSITION'",Integer.class,id)).isEqualTo(1);
        } finally {jdbc.execute("ALTER TABLE product_development DROP CONSTRAINT test_deny_development");}
    }

    @Test void manualCreationIsPermissionCheckedValidatedAndDoesNotCreateCandidates() throws Exception {
        String request=UUID.randomUUID().toString(); ObjectNode f=json.createObjectNode().put("name","手动新品需求");
        manual(null,request,f).andExpect(status().isUnauthorized());
        manual(selector,request,f).andExpect(status().isForbidden());
        manual(admin,"invalid",f).andExpect(status().isBadRequest());
        manual(admin,request,json.createObjectNode().put("name"," ")).andExpect(status().isBadRequest());
        manual(admin,request,f.deepCopy().put("purchaseCost",-1)).andExpect(status().isBadRequest());
        manual(admin,request,f.deepCopy().put("startDate","2026-11-01").put("endDate","2026-10-01")).andExpect(status().isBadRequest());
        String result=manual(productUser,request,f).andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceType").value("MANUAL")).andExpect(jsonPath("$.source").isEmpty())
            .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.version").value(0))
            .andReturn().getResponse().getContentAsString();
        String id=json.readTree(result).path("id").asText();
        assertThat(json.readTree(result).path("number").asText()).matches("ND-M-[0-9]{8,}");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_candidate",Integer.class)).isZero();
        mvc.perform(get("/api/v1/product-development/"+id).header("Authorization",selector)).andExpect(status().isOk())
            .andExpect(jsonPath("$.history[0].action").value("DEVELOPMENT_MANUAL_CREATE"));
        ObjectNode fields=fields(id); fields.put("developer","开发员").put("productName","新产品").put("startDate","2026-10-16").put("endDate","2026-11-01");
        update(id,productUser,0,fields).andExpect(status().isOk());
        transition(id,productUser,1,"IN_PROGRESS",null).andExpect(status().isOk());
        transition(id,productUser,2,"COMPLETED",null).andExpect(status().isBadRequest());
        transition(id,productUser,2,"VOID","取消").andExpect(status().isOk());
        update(id,productUser,3,fields).andExpect(status().isConflict());
        manual(productUser,request,f).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.status").value("VOID"));
        manual(admin,request,f).andExpect(status().isConflict());
        manual(productUser,request,f.deepCopy().put("name","更改内容")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='DEVELOPMENT_MANUAL_CREATE'",Integer.class,id)).isEqualTo(1);
    }

    @Test void concurrentManualCreatesReplayOnceAndAllowIndependentRequirements() throws Exception {
        String request=UUID.randomUUID().toString(); ObjectNode f=json.createObjectNode().put("name","并发新品需求");
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks=java.util.List.of(1,2).stream().map(n -> (java.util.concurrent.Callable<String>) () ->
                manual(admin,request,f).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).toList();
            var results=executor.invokeAll(tasks); String id=json.readTree(results.get(0).get()).path("id").asText();
            assertThat(json.readTree(results.get(1).get()).path("id").asText()).isEqualTo(id);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_development",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='DEVELOPMENT_MANUAL_CREATE'",Integer.class,id)).isEqualTo(1);
            String second=manual(admin,UUID.randomUUID().toString(),f).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(second).path("number").asText()).isNotEqualTo(json.readTree(results.get(0).get()).path("number").asText());
        } finally {executor.shutdownNow();}
    }

    private org.springframework.test.web.servlet.ResultActions manual(String token,String request,ObjectNode fields)throws Exception {
        var call=post("/api/v1/product-development").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("requestId",request,"fields",fields,"sourceType","CANDIDATE","candidateId","forged")));
        if(token!=null)call.header("Authorization",token);
        return mvc.perform(call);
    }

    private ObjectNode fields(String id)throws Exception {return (ObjectNode)json.readTree(mvc.perform(get("/api/v1/product-development/"+id).header("Authorization",admin)).andReturn().getResponse().getContentAsString()).path("item").path("fields");}
    private org.springframework.test.web.servlet.ResultActions update(String id,String token,long version,ObjectNode fields)throws Exception {
        return mvc.perform(put("/api/v1/product-development/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("version",version,"fields",fields))));
    }
    private org.springframework.test.web.servlet.ResultActions transition(String id,String token,long version,String target,String reason)throws Exception {
        ObjectNode body=json.createObjectNode().put("version",version).put("target",target);if(reason!=null)body.put("reason",reason);
        return mvc.perform(post("/api/v1/product-development/"+id+"/transition").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }
    private String candidate()throws Exception {
        String body=mvc.perform(post("/api/v1/selection/candidates").header("Authorization",selector).contentType(MediaType.APPLICATION_JSON)
            .content("{\"asinOrUrl\":\"B0DEV33001\",\"title\":\"开发测试\",\"score\":82}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("id").asText();
    }
    private void candidateTransition(String id,String target,int status)throws Exception {mvc.perform(post("/api/v1/selection/candidates/"+id+"/transition").header("Authorization",selector).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("status",target)))).andExpect(status().is(status));}
    private void approve(String id)throws Exception {candidateTransition(id,"REVIEWING",200);candidateTransition(id,"APPROVED",200);}
    private String user(String role)throws Exception {
        String id=UUID.randomUUID().toString(),name="dev-"+id.substring(0,8),password="Development-Test-42";
        jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES (?,?,?,?,?,'ENABLED',FALSE)",id,name,name,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);
        String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",name,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(body).path("token").asText();
    }
}
