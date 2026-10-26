package com.jiugeng.erp.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
class SelectionControllerTest {
    private static final String PASSWORD="Selection-Test-42";
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

    @BeforeEach void reset(){
        jdbc.update("DELETE FROM product_development");
        jdbc.update("DELETE FROM watch_new_product_event");
        jdbc.update("DELETE FROM selection_candidate_evidence"); jdbc.update("DELETE FROM selection_candidate_note");
        jdbc.update("DELETE FROM selection_candidate_tag"); jdbc.update("DELETE FROM selection_candidate");
        jdbc.update("DELETE FROM iam_password_reset_challenge"); jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session"); jdbc.update("DELETE FROM iam_user");
    }

    @Test void selectionWriterCanCompletePersistentCandidateWorkflow() throws Exception {
        String token=user("selector","SELECTION");
        String body=mvc.perform(post("/api/v1/selection/candidates").header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "asinOrUrl","https://www.amazon.com/dp/b0case12345/ref=abc","title","候选商品","score",81))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.asin").value("B0CASE12345"))
                .andExpect(jsonPath("$.source").value("MANUAL")).andReturn().getResponse().getContentAsString();
        String id=json.readTree(body).get("id").asText();

        mvc.perform(put("/api/v1/selection/candidates/{id}/evaluation",id).header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version",0,"category","Home","price",12.5,
                        "rating",4.6,"reviewCount",23,"owner","Alice","tags",java.util.List.of("机会","机会","低风险"),"selectionReason","值得复核"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags.length()").value(2));
        mvc.perform(post("/api/v1/selection/candidates/{id}/notes",id).header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" 首轮评审 \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notes[0].content").value("首轮评审"));
        mvc.perform(post("/api/v1/selection/candidates/{id}/evidence",id).header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"报价\",\"url\":\"https://evidence.example/quote?token=secret\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidence[0].url").value("https://evidence.example/quote?token=secret"));
        mvc.perform(post("/api/v1/selection/candidates/{id}/transition",id).header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"status\":\"REVIEWING\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/selection/candidates/{id}/transition",id).header("Authorization",bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":2,\"status\":\"REJECTED\",\"rejectionReason\":\"利润不足\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").value("利润不足"));
        mvc.perform(get("/api/v1/selection/candidates").header("Authorization",bearer(token)).param("keyword","alice").param("status","REJECTED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=?",Integer.class,id)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT after_value FROM audit_event WHERE action='CANDIDATE_EVIDENCE_APPEND'",String.class))
                .contains("REDACTED_HTTPS_URL").doesNotContain("token=secret");
    }

    @Test void validationUniquenessTransitionsAndPermissionsAreEnforced() throws Exception {
        String writer=user("selector","SELECTION"); String id=create(writer,"B0DUP000001");
        mvc.perform(post("/api/v1/selection/candidates").header("Authorization",bearer(writer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"asinOrUrl\":\"b0dup000001\",\"title\":\"duplicate\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANDIDATE_ASIN_EXISTS"));
        mvc.perform(post("/api/v1/selection/candidates/{id}/transition",id).header("Authorization",bearer(writer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"status\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));
        mvc.perform(post("/api/v1/selection/candidates/{id}/evidence",id).header("Authorization",bearer(writer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"bad\",\"url\":\"http://example.com\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANDIDATE_EVIDENCE_HTTPS_URL_REQUIRED"));
        String readonly=user("product","PRODUCT_MATERIAL");
        mvc.perform(get("/api/v1/selection/candidates").header("Authorization",bearer(readonly))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/selection/candidates").header("Authorization",bearer(readonly)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"asinOrUrl\":\"B0DENY00001\",\"title\":\"denied\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CAPABILITY_DENIED"));
    }

    @Test void formalProfileStartsWithNoSyntheticCandidates(){
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_candidate",Integer.class)).isZero();
    }

    @Test void concurrentCreatesAreSerializedByNormalizedAsinConstraint() throws Exception {
        String token=user("concurrent-selector","SELECTION");
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks=java.util.List.of(1,2).stream().map(number -> (java.util.concurrent.Callable<Integer>) () ->
                    mvc.perform(post("/api/v1/selection/candidates").header("Authorization",bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"asinOrUrl\":\"B0RACE00001\",\"title\":\"race " + number + "\"}"))
                            .andReturn().getResponse().getStatus()).toList();
            var statuses=executor.invokeAll(tasks).stream().map(future -> {
                try { return future.get(); } catch(Exception exception) { throw new RuntimeException(exception); }
            }).sorted().toList();
            assertThat(statuses).containsExactly(200,409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_candidate WHERE asin_normalized='B0RACE00001'",Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    private String create(String token,String asin)throws Exception {String body=mvc.perform(post("/api/v1/selection/candidates").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("asinOrUrl",asin,"title","candidate")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(body).get("id").asText();}
    private String user(String name,String role)throws Exception{String id=java.util.UUID.randomUUID().toString();var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();jdbc.update("INSERT INTO iam_user (id,login_name,display_name,password_hash,role,status,must_change_password) VALUES (?,?,?,?,?,'ENABLED',FALSE)",id,name,name,encoder.encode(PASSWORD),role);String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",name,"password",PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(body).get("token").asText();}
    private String bearer(String token){return "Bearer "+token;}
}
