package com.jiugeng.erp.iam;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class PhoneLoginTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired IamService service;
 static final String PHONE="15500009901", PASSWORD="Synthetic-Phone-42";
 String id;
 @BeforeEach void setup(){jdbc.update("DELETE FROM iam_session");jdbc.update("DELETE FROM iam_user");id=service.createUser("phone-user","Synthetic phone",PASSWORD,IamTypes.Role.SELECTION).id();jdbc.update("UPDATE iam_user SET phone=? WHERE id=?",PHONE,id);}
 org.springframework.test.web.servlet.ResultActions login(String identifier,String password) throws Exception {
 return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",identifier,"password",password))));
 }
 @Test void usernameAndPhoneResolveSameAccountAndPreserveFirstLoginAndAudit() throws Exception {
 for(String identifier:new String[]{"phone-user"," PHONE-USER ",PHONE,"155 0000-9901"}) {
 login(identifier,PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(id)).andExpect(jsonPath("$.user.loginName").value("phone-user")).andExpect(jsonPath("$.user.mustChangePassword").value(true));
 }
 assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_id=? AND action='LOGIN' AND actor_login_name='phone-user'",Integer.class,id)).isEqualTo(4);
 }
 @Test void bothIdentifiersShareFailureCounterAndLock() throws Exception {
 for(int i=0;i<4;i++)login(i%2==0?PHONE:"phone-user","wrong").andExpect(status().isUnauthorized());
 login(PHONE,"wrong").andExpect(status().isLocked());
 login("phone-user",PASSWORD).andExpect(status().isLocked());login(PHONE,PASSWORD).andExpect(status().isLocked());
 }
 @Test void disabledAndDeletedAccountsCannotLoginAndUpdatedPhoneTakesEffect() throws Exception {
 jdbc.update("UPDATE iam_user SET status='DISABLED' WHERE id=?",id);login(PHONE,PASSWORD).andExpect(status().isForbidden());
 jdbc.update("UPDATE iam_user SET status='ENABLED',phone='15500009902' WHERE id=?",id);login(PHONE,PASSWORD).andExpect(status().isUnauthorized());login("15500009902",PASSWORD).andExpect(status().isOk());
 jdbc.update("UPDATE iam_user SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",id);login("15500009902",PASSWORD).andExpect(status().isUnauthorized());login("phone-user",PASSWORD).andExpect(status().isUnauthorized());
 }
 @Test void rejectsLegacyAmbiguityAndNewIdentifierCollision() throws Exception {
 assertThatThrownBy(()->service.createUser(PHONE,"Collision",PASSWORD,IamTypes.Role.SELECTION)).isInstanceOf(IamException.class);
 var other=service.createUser("other-user","Other",PASSWORD,IamTypes.Role.SELECTION);
 jdbc.update("UPDATE iam_user SET login_name=? WHERE id=?",PHONE,other.id());
 login(PHONE,PASSWORD).andExpect(status().isUnauthorized());
 login("phone-user",PASSWORD).andExpect(status().isOk());
 }
 @Test void unknownPhoneAndMalformedIdentifierReturnGenericFailure() throws Exception {
 login("15500009999",PASSWORD).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
 login("bad@name",PASSWORD).andExpect(status().isUnauthorized());
 }
}
