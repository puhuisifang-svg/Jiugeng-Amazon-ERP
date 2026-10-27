package com.jiugeng.erp.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
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
class MonitoringControllerTest {
    static final String ROOT="/api/v1/product-monitoring";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MonitoringService monitoring;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @BeforeEach void clean(){jdbc.update("DELETE FROM monitor_snapshot");jdbc.update("DELETE FROM monitor_target");jdbc.update("DELETE FROM monitor_request");jdbc.update("DELETE FROM monitor_preference");}

    @Test void manualFieldsRemoveRestoreAndVersionConflicts()throws Exception {
        String token=login("SELECTION"),id=create(token,"B0TEST0001");
        mvc.perform(put(ROOT+"/"+id).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(put(ROOT+"/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",0,"field","sales30Days","value","123")))).andExpect(status().isOk()).andExpect(jsonPath("$.sales30Days").value(123));
        mvc.perform(put(ROOT+"/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",0,"field","remark","value","stale")))).andExpect(status().isConflict());
        mvc.perform(put(ROOT+"/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1,"field","sales30Days","value","-1")))).andExpect(status().isBadRequest());
        mvc.perform(put(ROOT+"/"+id).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version",1,"field","remark","value","Synthetic note")))).andExpect(status().isOk()).andExpect(jsonPath("$.remark").value("Synthetic note"));
        snapshot(token,id,2,"2026-01-01T00:00:00Z","COMPLETE",fields("First",10),"MANUAL");
        snapshot(token,id,3,"2026-01-02T00:00:00Z","COMPLETE",fields("Second",10),"MANUAL");
        mvc.perform(get(ROOT+"/"+id).header("Authorization",token)).andExpect(jsonPath("$.target.sales30Days").value(123)).andExpect(jsonPath("$.target.latestChanges[0].field").value("title"));
        mvc.perform(post(ROOT+"/batch").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targets",List.of(Map.of("id",id,"version",4)),"action","REMOVE")))).andExpect(jsonPath("$[0].status").value("UPDATED"));
        mvc.perform(get(ROOT).header("Authorization",token)).andExpect(jsonPath("$.total").value(0));
        assertThat(create(token,"B0TEST0001")).isEqualTo(id);
        mvc.perform(get(ROOT+"/"+id).header("Authorization",token)).andExpect(jsonPath("$.target.enabled").value(true)).andExpect(jsonPath("$.snapshots.length()").value(2)).andExpect(jsonPath("$.target.remark").value("Synthetic note"));
    }

    @Test void allRolesCanWriteButAnonymousCannotAndNoBusinessAuditIsCreated()throws Exception {
        mvc.perform(get(ROOT)).andExpect(status().isUnauthorized());
        mvc.perform(post(ROOT).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        for(String role:List.of("ADMIN","PRODUCT_MATERIAL","SELECTION")) {
            String token=login(role);String id=create(token,"B0TEST0001");
            mvc.perform(get(ROOT+"/"+id).header("Authorization",token)).andExpect(status().isOk());
            mvc.perform(post(ROOT+"/batch").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("targets",List.of(Map.of("id",id,"version",version(token,id))),"action","ASSIGN_SELF"))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("UPDATED"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitor_target",Integer.class)).isEqualTo(1);
    }

    @Test void createDeduplicatesValidatesAndDetectsIdempotencyConflicts()throws Exception {
        String token=login("SELECTION");String key=UUID.randomUUID().toString();
        var input=Map.of("requestId",key,"asins",List.of("b0test0001"," B0TEST0001 ","bad"));
        String first=mvc.perform(post(ROOT).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("CREATED")).andExpect(jsonPath("$[1].status").value("EXISTS")).andExpect(jsonPath("$[2].status").value("INVALID"))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post(ROOT).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(content().json(first));
        mvc.perform(post(ROOT).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId",key,"asins",List.of("B0TEST0002")))))
                .andExpect(status().isConflict());
        mvc.perform(get(ROOT).param("size","500").header("Authorization",token)).andExpect(status().isBadRequest());
    }

    @Test void concurrentRequestsCannotDuplicateTargets()throws Exception {
        String token=login("PRODUCT_MATERIAL");
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<String>> results=new ArrayList<>();
            for(int i=0;i<2;i++) results.add(pool.submit(()->create(token,"B0TEST0001")));
            assertThat(results.get(0).get(10,TimeUnit.SECONDS)).isEqualTo(results.get(1).get(10,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitor_target",Integer.class)).isEqualTo(1);
    }

    @Test void baselineChangesReplayPartialFailureOutOfOrderAndSourceAreDistinct()throws Exception {
        String token=login("SELECTION"),id=create(token,"B0TEST0001");
        JsonNode first=snapshot(token,id,0,"2026-01-01T00:00:00Z","COMPLETE",fields("First",10),"MANUAL");
        assertThat(first.get("decision").asText()).isEqualTo("BASELINE");assertThat(first.get("changes").size()).isZero();
        JsonNode second=snapshot(token,id,1,"2026-01-02T00:00:00Z","COMPLETE",fields("Second",12),"MANUAL");
        assertThat(second.get("decision").asText()).isEqualTo("CHANGED");assertThat(second.get("changes").size()).isEqualTo(2);
        assertThat(snapshot(token,id,2,"2026-01-03T00:00:00Z","PARTIAL",Map.of("title","Partial"),"MANUAL").get("decision").asText()).isEqualTo("PARTIAL");
        snapshot(token,id,3,"2026-01-04T00:00:00Z","FAILED",Map.of(),"MANUAL");
        assertThat(snapshot(token,id,4,"2026-01-01T00:00:00Z","COMPLETE",fields("Old",0),"MANUAL").get("decision").asText()).isEqualTo("OUT_OF_ORDER");
        mvc.perform(get(ROOT+"/"+id).header("Authorization",token)).andExpect(jsonPath("$.target.fields.title").value("Second")).andExpect(jsonPath("$.target.fields.price").value(12));
        assertThat(snapshot(token,id,5,"2026-01-05T00:00:00Z","COMPLETE",fields("Demo",20),"SYNTHETIC").get("decision").asText()).isEqualTo("SOURCE_BASELINE");
        mvc.perform(get(ROOT+"/"+id+"/snapshots").header("Authorization",token)).andExpect(jsonPath("$.total").value(6));
    }

    @Test void snapshotRetryIsIdempotentAndStaleOrPausedSubmissionRejected()throws Exception {
        String token=login("ADMIN"),id=create(token,"B0TEST0001");
        Map<String,Object> input=new HashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"version",0,"observedAt","2026-01-01T00:00:00Z","source","MANUAL","quality","COMPLETE","fields",fields("Baseline",0)));
        String first=mvc.perform(post(ROOT+"/"+id+"/snapshots").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post(ROOT+"/"+id+"/snapshots").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(content().json(first));
        input.put("fields",fields("Different",1));
        mvc.perform(post(ROOT+"/"+id+"/snapshots").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isConflict());
        input.put("requestId",UUID.randomUUID().toString());
        mvc.perform(post(ROOT+"/"+id+"/snapshots").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isConflict());
        mvc.perform(post(ROOT+"/batch").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targets",List.of(Map.of("id",id,"version",1)),"action","PAUSE")))).andExpect(jsonPath("$[0].status").value("UPDATED"));
        input.put("version",2);
        mvc.perform(post(ROOT+"/"+id+"/snapshots").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitor_snapshot",Integer.class)).isEqualTo(1);
    }

    @Test void completeMissingFieldsRejectedAndUnknownIsNotUnobserved()throws Exception {
        String token=login("PRODUCT_MATERIAL"),id=create(token,"B0TEST0001");
        Map<String,Object> input=new HashMap<>(Map.of("requestId",UUID.randomUUID().toString(),"version",0,"observedAt","2026-01-01T00:00:00Z","source","MANUAL","quality","COMPLETE","fields",Map.of("title","Missing")));
        mvc.perform(post(ROOT+"/"+id+"/snapshots").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isBadRequest());
        snapshot(token,id,0,"2026-01-01T00:00:00Z","COMPLETE",fields("First",10),"MANUAL");
        Map<String,Object> unknown=fields("First",10);unknown.put("price",null);
        JsonNode result=snapshot(token,id,1,"2026-01-02T00:00:00Z","COMPLETE",unknown,"MANUAL");
        assertThat(result.at("/changes/0/after").isNull()).isTrue();
        mvc.perform(get(ROOT+"/"+id).header("Authorization",token)).andExpect(jsonPath("$.target.fields.price").doesNotExist());
        var malicious=fields("Text",1);malicious.put("imageUrl","javascript:alert(1)");input.put("fields",malicious);
        mvc.perform(post(ROOT+"/"+id+"/snapshots").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(input))).andExpect(status().isBadRequest());
    }

    @Test void personalPreferencesAndFollowAreIsolatedAndFilterWorks()throws Exception {
        String a=login("ADMIN"),b=login("SELECTION"),id=create(a,"B0TEST0001");
        mvc.perform(put(ROOT+"/"+id+"/follow").header("Authorization",a).contentType(MediaType.APPLICATION_JSON).content("{\"followed\":true}")).andExpect(status().isOk());
        mvc.perform(put(ROOT+"/preferences").header("Authorization",a).contentType(MediaType.APPLICATION_JSON).content("{\"columns\":[\"price\"]}")).andExpect(status().isOk());
        mvc.perform(get(ROOT).param("followed","true").header("Authorization",a)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get(ROOT).param("followed","true").header("Authorization",b)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get(ROOT+"/preferences").header("Authorization",b)).andExpect(jsonPath("$.columns.length()").value(7));
        mvc.perform(get(ROOT).param("q","b0test0001").header("Authorization",b)).andExpect(jsonPath("$.total").value(1));
        mvc.perform(put(ROOT+"/preferences").header("Authorization",a).contentType(MediaType.APPLICATION_JSON).content("{\"columns\":[\"invalid\"]}")).andExpect(status().isBadRequest());
    }

    @Test void quotaAndResumeAreAtomic()throws Exception {
        String token=login("ADMIN");String id=create(token,"B0TEST0001");
        for(int i=0;i<999;i++) jdbc.update("INSERT INTO monitor_target(id,marketplace,asin,tags_json,remark) VALUES(?,'US',?,'[]','')",UUID.randomUUID().toString(),String.format("Z%09d",i));
        mvc.perform(post(ROOT).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId",UUID.randomUUID().toString(),"asins",List.of("B0TEST0002")))))
                .andExpect(jsonPath("$[0].status").value("LIMIT"));
        mvc.perform(post(ROOT+"/batch").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targets",List.of(Map.of("id",id,"version",0)),"action","PAUSE")))).andExpect(jsonPath("$[0].status").value("UPDATED"));
        create(token,"B0TEST0002");
        mvc.perform(post(ROOT+"/batch").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("targets",List.of(Map.of("id",id,"version",1)),"action","ENABLE")))).andExpect(jsonPath("$[0].status").value("LIMIT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monitor_target WHERE enabled=TRUE",Integer.class)).isEqualTo(1000);
    }

    @Test void absentPublicPricePreservesHistoricalValueWithoutFalseChange() throws Exception {
        String token=login("ADMIN"),id=create(token,"B0TEST0999");
        tx.executeWithoutResult(t->monitoring.snapshotValidated(id,new MonitoringTypes.SnapshotInput("public-first",0,java.time.Instant.parse("2026-01-01T00:00:00Z"),"EXTERNAL:amazon-public-us","COMPLETE",mapper.valueToTree(fields("First",19)),""),true));
        var observed=mapper.createObjectNode().put("title","Second");
        var snapshot=tx.execute(t->monitoring.snapshotValidated(id,new MonitoringTypes.SnapshotInput("public-second",1,java.time.Instant.parse("2026-01-02T00:00:00Z"),"EXTERNAL:amazon-public-us","PARTIAL",observed,"Price not observed"),true));
        var stored=mapper.readTree(jdbc.queryForObject("SELECT baseline_json FROM monitor_target WHERE id=?",String.class,id));
        assertThat(stored.path("price").asInt()).isEqualTo(19);assertThat(stored.path("title").asText()).isEqualTo("Second");
        assertThat(snapshot.fields().has("price")).isFalse();assertThat(snapshot.changes().size()).isEqualTo(1);assertThat(snapshot.changes().get(0).path("field").asText()).isEqualTo("title");
    }

    private Map<String,Object> fields(String title,int price){Map<String,Object> f=new LinkedHashMap<>();f.put("title",title);f.put("brand",null);f.put("imageUrl",null);f.put("bullets",List.of("One","Two"));f.put("price",price);f.put("currency","USD");f.put("availability","AVAILABLE");return f;}
    private JsonNode snapshot(String token,String id,long version,String at,String quality,Object fields,String source)throws Exception {
        return mapper.readTree(mvc.perform(post(ROOT+"/"+id+"/snapshots").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId",UUID.randomUUID().toString(),"version",version,"observedAt",at,"quality",quality,"source",source,"fields",fields,"reason","test observation")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private long version(String token,String id)throws Exception{return mapper.readTree(mvc.perform(get(ROOT+"/"+id).header("Authorization",token)).andReturn().getResponse().getContentAsString()).at("/target/version").asLong();}
    private String create(String token,String asin)throws Exception{return mapper.readTree(mvc.perform(post(ROOT).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("requestId",UUID.randomUUID().toString(),"asins",List.of(asin))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/0/id").asText();}
    private String login(String role)throws Exception{
        String id=UUID.randomUUID().toString(),name="monitor-"+id.substring(0,8),password="Monitor-Test-42";
        jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,name,name,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);
        return "Bearer "+mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(Map.of("loginName",name,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
    }
    private String json(Object value)throws Exception{return mapper.writeValueAsString(value);}
}
