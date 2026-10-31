package com.jiugeng.erp.iam;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties="user_defaults_key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=") @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class UserDefaultsControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired IamService iam;
    static final String PASSWORD="Synthetic-Preset-Test-42!";
    String admin;
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM iam_session"); jdbc.update("DELETE FROM iam_user");
        iam.createUser("preset-admin","Synthetic admin",PASSWORD,IamTypes.Role.ADMIN);
        iam.createUser("preset-member","Synthetic member",PASSWORD,IamTypes.Role.SELECTION);
        jdbc.update("UPDATE iam_user SET must_change_password=FALSE"); admin=login("preset-admin");
    }
    String login(String name) throws Exception {
        return "Bearer "+json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",name,"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();
    }
    Map<String,Object> edit(String id,String name,List<String> permissions) {
        return Map.of("name",name,"description","Synthetic editable preset","status","ENABLED","permissions",permissions,
            "version",jdbc.queryForObject("SELECT version FROM iam_role WHERE id=?",Long.class,id),"reason","Synthetic preset verification");
    }
    @Test void defaultsAreAdminOnlyEncryptedAndExcludedFromAudit() throws Exception {
        String body=json.writeValueAsString(Map.of("initialPassword","Synthetic-Default-42"));
        mvc.perform(put("/api/v1/settings/user-defaults").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/settings/user-defaults").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.initialPassword").value("Synthetic-Default-42"));
        assertThat(jdbc.queryForObject("SELECT encrypted_password FROM iam_user_defaults WHERE id=1",String.class)).doesNotContain("Synthetic-Default-42").isNotBlank();
        assertThat(jdbc.queryForObject("SELECT after_value FROM audit_event WHERE action='USER_DEFAULT_PASSWORD_UPDATE' ORDER BY occurred_at DESC LIMIT 1",String.class)).doesNotContain("Synthetic-Default-42").contains("configured");
        String member=login("preset-member");
        mvc.perform(get("/api/v1/settings/user-defaults").header("Authorization",member)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/settings/user-defaults").header("Authorization",member).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/user-defaults")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/login-settings")).andExpect(status().isOk()).andExpect(jsonPath("$.initialPassword").doesNotExist());
    }
    @Test void validatesAndClearsDefaultPassword() throws Exception {
        for(String password:List.of("short","12345678","abcdefgh","A1".repeat(65))) {
            mvc.perform(put("/api/v1/settings/user-defaults").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("initialPassword",password)))).andExpect(status().isBadRequest());
        }
        mvc.perform(put("/api/v1/settings/user-defaults").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"initialPassword\":\"\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/settings/user-defaults").header("Authorization",admin)).andExpect(jsonPath("$.initialPassword").value(""));
    }
    @Test void userHistoryPagesBeyondPreviousTwoHundredLimit() throws Exception {
        String id=jdbc.queryForObject("SELECT id FROM iam_user WHERE login_name='preset-member'",String.class);
        for(int i=0;i<205;i++) jdbc.update("INSERT INTO audit_event(id,category,action,actor_login_name,actor_display_name,resource_type,resource_id,outcome,occurred_at) VALUES (?,'ACCOUNT','ACCOUNT_PROFILE_UPDATE','test','Synthetic','ACCOUNT',?,'SUCCESS',CURRENT_TIMESTAMP)",UUID.randomUUID().toString(),id);
        mvc.perform(get("/api/v1/users/"+id+"/history?page=21&size=10").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(206)).andExpect(jsonPath("$.items.length()").value(6));
        mvc.perform(get("/api/v1/users/"+id+"/history?page=0").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/"+id+"/history?size=101").header("Authorization",admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/users/"+id+"/history").header("Authorization",login("preset-member"))).andExpect(status().isForbidden());
    }
}
