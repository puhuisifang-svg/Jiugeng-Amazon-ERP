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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class PresetRoleControllerTest {
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
    @Test void listsAdminFirstAndUpdatesPresetPermissionsWithRevocationAndAudit() throws Exception {
        mvc.perform(get("/api/v1/roles").header("Authorization",admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("ADMIN"));
        String member=login("preset-member"); var request=edit("SELECTION","选品测试职责",List.of("products.write"));
        mvc.perform(post("/api/v1/roles/SELECTION/preview").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/roles/SELECTION").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("选品测试职责"));
        assertThat(jdbc.queryForList("SELECT permission_code FROM iam_role_permission WHERE role_id='SELECTION'",String.class)).containsExactlyInAnyOrder("products.read","products.write");
        mvc.perform(get("/api/v1/auth/me").header("Authorization",member)).andExpect(status().isUnauthorized());
        String renewed=login("preset-member");
        mvc.perform(get("/api/v1/roles").header("Authorization",renewed)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id='SELECTION' AND action='ROLE_UPDATE'",Integer.class)).isGreaterThan(0);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings={" ","单"})
    void savesAndAuditsWithoutMandatoryReason(String reason) throws Exception {
        String member=login("preset-member");
        var request=new HashMap<>(edit("SELECTION","选品测试职责",List.of("products.read")));
        if(reason==null) request.remove("reason"); else request.put("reason",reason);
        String body=json.writeValueAsString(request);
        mvc.perform(post("/api/v1/roles/SELECTION/preview").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/roles/SELECTION").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization",member)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id='SELECTION' AND action='ROLE_UPDATE'",Integer.class)).isGreaterThan(0);
    }
    @Test void rejectsOverlongOptionalReasonWithoutUpdating() throws Exception {
        var request=new HashMap<>(edit("SELECTION","选品测试职责",List.of("products.read")));
        request.put("reason","x".repeat(201));
        for(String endpoint:List.of("preview","save")) {
            var call=endpoint.equals("preview")?post("/api/v1/roles/SELECTION/preview"):put("/api/v1/roles/SELECTION");
            mvc.perform(call.header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REASON_TOO_LONG"));
        }
        assertThat(jdbc.queryForObject("SELECT version FROM iam_role WHERE id='SELECTION'",Long.class)).isEqualTo(request.get("version"));
    }
    @Test void deletesUnassignedPresetButRetainsHistory() throws Exception {
        long version=jdbc.queryForObject("SELECT version FROM iam_role WHERE id='PRODUCT_MATERIAL'",Long.class);
        mvc.perform(delete("/api/v1/roles/PRODUCT_MATERIAL").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version",version,"reason","Synthetic deletion")))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM iam_role WHERE id='PRODUCT_MATERIAL' AND deleted_at IS NOT NULL",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id='PRODUCT_MATERIAL' AND action='ROLE_DELETE'",Integer.class)).isGreaterThan(0);
    }
    @Test void cannotDeletePresetThatStillHasMembers() throws Exception {
        long version=jdbc.queryForObject("SELECT version FROM iam_role WHERE id='SELECTION'",Long.class);
        mvc.perform(delete("/api/v1/roles/SELECTION").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("version",version,"reason","Synthetic deletion"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLE_HAS_MEMBERS"));
    }
    @Test void newCustomRoleUsersDoNotDependOnDeletedPresets() throws Exception {
        jdbc.update("DELETE FROM iam_user WHERE login_name='preset-member'");
        jdbc.update("UPDATE iam_role SET deleted_at=CURRENT_TIMESTAMP WHERE id='SELECTION'");
        jdbc.update("INSERT INTO iam_role(id,name,description,kind,status) VALUES ('synthetic-role','Synthetic role','','CUSTOM','ENABLED')");
        jdbc.update("INSERT INTO iam_role_permission(role_id,permission_code) VALUES ('synthetic-role','products.read')");
        mvc.perform(post("/api/v1/users/with-roles").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName","preset-new","displayName","Synthetic new","initialPassword",PASSWORD,"roleIds",List.of("synthetic-role")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0].id").value("synthetic-role"));
        mvc.perform(post("/api/v1/users").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName","preset-legacy","displayName","Synthetic legacy","initialPassword",PASSWORD,"role","SELECTION"))))
            .andExpect(status().isNotFound());
    }
    @Test void cannotEditAdminEvenWithKnownCurrentVersion() throws Exception {
        mvc.perform(put("/api/v1/roles/ADMIN").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(edit("ADMIN","管理员测试",List.of("products.read")))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ADMIN_ROLE_PROTECTED"));
    }
}
