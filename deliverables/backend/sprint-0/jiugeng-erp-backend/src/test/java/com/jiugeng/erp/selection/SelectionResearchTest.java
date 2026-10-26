package com.jiugeng.erp.selection;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class SelectionResearchTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @Test void decimalProfitAndMissingCostsAreNotSilentlyRoundedOrZeroed(){
        var input=input(0);var result=SelectionResearch.calculate(input);
        assertThat(result.profit()).isEqualByComparingTo("6");assertThat(result.margin()).isEqualByComparingTo("0.3");
        assertThat(result.breakEven()).isEqualByComparingTo("11.4285714286");
        assertThat(result.lowPriceProfit()).isEqualByComparingTo("4.6");assertThat(result.highCostProfit()).isEqualByComparingTo("5.2");
        var missing=new SelectionResearch.EstimateInput(0L,"test-missing","test",new BigDecimal("20"),"USD",null,new BigDecimal(".15"),new BigDecimal(".1"),new BigDecimal(".05"),LocalDate.now(),"test");
        assertThatThrownBy(()->SelectionResearch.calculate(missing)).isInstanceOf(SelectionException.class);
    }
    @Test void approvalRequiresCompleteResearchFreezesItAndProtectsVersions()throws Exception{
        String token=user("SELECTION"),readonly=user("PRODUCT_MATERIAL");
        String id=json.readTree(mvc.perform(post("/api/v1/selection/candidates").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
            .content("{\"asinOrUrl\":\"B0RES98001\",\"title\":\"Synthetic research\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
        try {
            mvc.perform(post(base(id)+"/transition").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"status\":\"REVIEWING\"}")).andExpect(status().isBadRequest());
            mvc.perform(put(base(id)+"/evaluation").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"owner\":\"Researcher\",\"selectionReason\":\"Market evidence\"}")).andExpect(status().isOk());
            mvc.perform(put(base(id)+"/evaluation").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"owner\":\"stale\"}")).andExpect(status().isConflict());
            mvc.perform(post(base(id)+"/evidence").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Synthetic evidence\",\"url\":\"https://example.com/evidence\"}")).andExpect(status().isOk());
            String estimateBody=json.writeValueAsString(input(1));
            String estimate=json.readTree(mvc.perform(post(base(id)+"/estimates").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(estimateBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
            mvc.perform(post(base(id)+"/estimates").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(estimateBody)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(estimate));
            ObjectNode changed=(ObjectNode)json.readTree(estimateBody);changed.put("price",30);
            mvc.perform(post(base(id)+"/estimates").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(changed.toString())).andExpect(status().isConflict());
            var research=new SelectionResearch.Research(2L,"Customers","Difference","Supplier","ACCEPTABLE","Checked synthetic evidence",Collections.nCopies(5,new SelectionResearch.Criterion(4,"Evidence checked")),estimate,"Proceed with prototype");
            mvc.perform(put(base(id)+"/research").header("Authorization",readonly).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(research))).andExpect(status().isForbidden());
            mvc.perform(put(base(id)+"/research").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(research))).andExpect(status().isOk()).andExpect(jsonPath("$.weightedScore").value(80));
            mvc.perform(post(base(id)+"/transition").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":3,\"status\":\"REVIEWING\"}")).andExpect(status().isOk());
            mvc.perform(post(base(id)+"/transition").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":4,\"status\":\"APPROVED\"}")).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("SELECT selection_research_json FROM product_development WHERE id=?",String.class,id)).contains("Proceed with prototype",estimate);
            mvc.perform(put(base(id)+"/research").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new SelectionResearch.Research(5L,"change","","","PENDING","",research.criteria(),estimate,"")))).andExpect(status().isConflict());
            mvc.perform(post(base(id)+"/notes").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Append after approval\"}")).andExpect(status().isOk());
            mvc.perform(get(base(id)+"/research")).andExpect(status().isUnauthorized());
        }finally{jdbc.update("DELETE FROM product_development WHERE id=?",id);jdbc.update("DELETE FROM selection_candidate WHERE id=?",id);}
    }
    private static SelectionResearch.EstimateInput input(long version){return new SelectionResearch.EstimateInput(version,"estimate-test-098","Base",new BigDecimal("20"),"USD",List.of(cost("purchase","8"),cost("pack","0"),cost("freight","0"),cost("fulfilment","0"),cost("other","0")),new BigDecimal(".15"),new BigDecimal(".10"),new BigDecimal(".05"),LocalDate.now(),"Synthetic fee evidence");}
    private static SelectionResearch.Cost cost(String name,String amount){return new SelectionResearch.Cost(name,new BigDecimal(amount),"USD",BigDecimal.ONE,"Synthetic confirmed amount");}
    private String base(String id){return "/api/v1/selection/candidates/"+id;}
    private String user(String role)throws Exception{String id=UUID.randomUUID().toString(),password=UUID.randomUUID().toString();jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"Synthetic researcher",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),role);String response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loginName",id,"password",password)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return "Bearer "+json.readTree(response).path("token").asText();}
}
