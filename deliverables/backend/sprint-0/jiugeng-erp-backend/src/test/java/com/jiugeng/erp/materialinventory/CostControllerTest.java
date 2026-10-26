package com.jiugeng.erp.materialinventory;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.jiugeng.erp.materialinventory.CostTypes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CostControllerTest {
    @Autowired CostService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    String product,sku;
    final AuthenticatedActor actor=new AuthenticatedActor(){public String userId(){return "cost-test";}public String loginName(){return "cost-test";}public String displayName(){return "成本测试";}};
    static BigDecimal n(String v){return new BigDecimal(v);}
    @BeforeEach void setup(){
        product=UUID.randomUUID().toString();sku=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES(?,?,?,'成本测试产品','ACTIVE','test','test')",product,product,product);
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES(?,?,?,?,'四方罩','ACTIVE',?)",sku,product,sku,sku,sku);
    }
    String material(String price){String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO auxiliary_material(id,material_code,material_code_normalized,name,material_type,unit,status,created_by,updated_by) VALUES(?,?,?,'测试物料','包装','件','ACTIVE','test','test')",id,id,id);
        if(price!=null)service.savePrice(id,new PriceInput(n(price),"件","MANUAL",null,"测试核价",-1L,true),actor);return id;}
    MaterialInput line(String id,String quantity,String per){return new MaterialInput(id,n(quantity),"件",BigDecimal.ONE,n(per),BigDecimal.ZERO,"");}
    Input input(List<MaterialInput> materials,List<LaborInput> labor){return new Input("件",BigDecimal.ONE,materials,labor,"无需物料","无需人工");}
    LaborInput labor(String price,String basis,String per){String id=service.operation(null,new OperationInput(UUID.randomUUID().toString(),true,-1L),actor).id();return new LaborInput(id,n(price),basis,BigDecimal.ONE,n(per),"测试工价");}
    Version save(Input input){var preview=service.preview(sku,input);return service.save(sku,new Save(input,preview.sourceHash(),null,"首次核算"),actor);}
    @Test void sampleCostsAndBoxAllocationAreExact(){
        var lines=new ArrayList<MaterialInput>();for(String p:List.of("4.7","2.33","1.7","0.7","0.3","0.12"))lines.add(line(material(p),"1","1"));lines.add(line(material("7"),"1","50"));
        var labor=new ArrayList<LaborInput>();for(String p:List.of("1","2.2","0.7","0.4","0.4","0.15","0.5","0.2"))labor.add(labor(p,"PIECE","1"));
        var v=save(input(lines,labor));assertThat(v.snapshot().materialTotal()).isEqualTo("9.990000");assertThat(v.snapshot().laborTotal()).isEqualTo("5.550000");assertThat(v.snapshot().total()).isEqualTo("15.540000");
        var published=service.publish(v.id(),new Publish(v.revision(),"核准"),actor);assertThat(published.status()).isEqualTo("EFFECTIVE");assertThat(published.stale()).isFalse();
        assertThat(jdbc.queryForObject("SELECT standard_quantity FROM product_auxiliary_material WHERE variant_id=? AND auxiliary_material_id=?",BigDecimal.class,sku,lines.get(6).materialId())).isEqualByComparingTo("0.02");
    }
    @Test void pricesRequireExplicitNetConfirmationAndOptimisticVersion(){
        String id=material(null);assertThatThrownBy(()->service.savePrice(id,new PriceInput(n("2"),"件","MANUAL",null,"原因",-1L,false),actor)).hasMessageContaining("确认");
        service.savePrice(id,new PriceInput(n("0"),"件","MANUAL",null,"免费物料",-1L,true),actor);
        assertThatThrownBy(()->service.savePrice(id,new PriceInput(n("3"),"件","MANUAL",null,"原因",-1L,true),actor)).hasMessageContaining("刷新");
        assertThatThrownBy(()->service.savePrice(id,new PriceInput(n("3"),"件","PURCHASE","missing","原因",0L,true),actor)).hasMessageContaining("有效人民币采购");
    }
    @Test void missingPriceBlocksPublicationAndPriceChangeKeepsSnapshot(){
        String id=material(null);var v=save(input(List.of(line(id,"1","1")),List.of()));assertThat(v.snapshot().errors()).isNotEmpty();
        assertThatThrownBy(()->service.publish(v.id(),new Publish(v.revision(),"核准"),actor)).hasMessageContaining("缺少");
        service.savePrice(id,new PriceInput(n("2"),"件","MANUAL",null,"核定",-1L,true),actor);
        assertThat(service.version(v.id()).stale()).isTrue();assertThatThrownBy(()->service.publish(v.id(),new Publish(v.revision(),"核准"),actor)).hasMessageContaining("变化");
        var fresh=service.preview(sku,v.input());var revised=service.save(sku,new Save(v.input(),fresh.sourceHash(),v.revision(),"补价格"),actor);var effective=service.publish(revised.id(),new Publish(revised.revision(),"核准"),actor);
        service.savePrice(id,new PriceInput(n("3"),"件","MANUAL",null,"涨价",0L,true),actor);
        assertThat(service.version(effective.id()).snapshot().total()).isEqualTo("2.000000");assertThat(service.version(effective.id()).stale()).isTrue();
        var second=save(effective.input());service.publish(second.id(),new Publish(second.revision(),"新价格"),actor);
        assertThat(service.version(effective.id()).status()).isEqualTo("HISTORICAL");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sku_cost_version WHERE variant_id=? AND status='EFFECTIVE'",Integer.class,sku)).isEqualTo(1);
    }
    @Test void laborBatchAndBoxDivisorsAreIndependent(){
        var in=new Input("件",n("50"),List.of(),List.of(labor("100","BATCH","1"),labor("10","BOX","20")),"无需物料","");
        assertThat(service.preview(sku,in).laborTotal()).isEqualTo("2.500000");
    }
    @Test void staleDraftRevisionAndDisabledSourceCannotPublish(){
        String id=material("1");var v=save(input(List.of(line(id,"1","1")),List.of()));
        assertThatThrownBy(()->service.save(sku,new Save(v.input(),v.sourceHash(),null,"重复草稿"),actor)).hasMessageContaining("草稿");
        jdbc.update("UPDATE auxiliary_material SET status='DISABLED' WHERE id=?",id);
        assertThatThrownBy(()->service.publish(v.id(),new Publish(v.revision(),"核准"),actor)).hasMessageContaining("变化");
        assertThat(service.version(v.id()).snapshot().total()).isEqualTo("1.000000");
    }
    @Test void csvEscapesFormulaPrefixesAndQuotes(){var b=new StringBuilder();CostController.row(b," =SUM(1,2)\nmore","a\"b","普通名称");assertThat(b.toString()).startsWith("\"' =SUM(1,2)").contains("a\"\"b");}
    String login(String role)throws Exception{String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO iam_user(id,login_name,display_name,password_hash,role,status,must_change_password) VALUES(?,?,?,?,?,'ENABLED',FALSE)",id,id,"成本测试",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Synthetic-Cost-42"),role);var response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("loginName",id,"password","Synthetic-Cost-42")))).andExpect(status().isOk()).andReturn().getResponse();return "Bearer "+mapper.readTree(response.getContentAsString()).get("token").asText();}
    @Test void capabilityAndValidationGates()throws Exception{
        String reader=login("SELECTION"),writer=login("PRODUCT_MATERIAL"),admin=login("ADMIN");
        mvc.perform(get("/api/v1/product-costs").header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/product-costs").header("Authorization",writer)).andExpect(status().isOk());
        var v=save(input(List.of(),List.of()));
        mvc.perform(post("/api/v1/product-costs/versions/"+v.id()+"/publish").header("Authorization",writer).contentType(MediaType.APPLICATION_JSON).content("{\"revision\":0,\"reason\":\"test\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/product-costs/skus/"+sku+"/preview").header("Authorization",admin).contentType(MediaType.APPLICATION_JSON).content("{\"unit\":\"件\",\"baseQuantity\":0,\"materials\":[],\"labor\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/product-costs/versions/"+v.id()+"/export").header("Authorization",reader)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/product-costs/versions/"+v.id()+"/export").header("Authorization",writer)).andExpect(status().isOk());
    }
    @Test void staleFilterIsAppliedBeforePagination(){
        String id=material("1");var v=save(input(List.of(line(id,"1","1")),List.of()));service.publish(v.id(),new Publish(v.revision(),"确认"),actor);
        service.savePrice(id,new PriceInput(n("2"),"件","MANUAL",null,"调整",0L,true),actor);
        var list=service.list("",product,"STALE",0,1);assertThat(list.totalCount()).isEqualTo(1);assertThat(list.items().getFirst().stale()).isTrue();
        assertThat(service.list("",product,"STALE",1,1).items()).isEmpty();
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentNewDraftsProduceOneWinner()throws Exception{
        var executor=java.util.concurrent.Executors.newFixedThreadPool(2);var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{
            var in=input(List.of(),List.of());var hash=service.preview(sku,in).sourceHash();
            java.util.concurrent.Callable<String> task=()->{ready.countDown();start.await(5,java.util.concurrent.TimeUnit.SECONDS);try{service.save(sku,new Save(in,hash,null,"并发测试"),actor);return "saved";}catch(MaterialException e){return e.code();}};
            var a=executor.submit(task);var b=executor.submit(task);assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder("saved","COST_CONFLICT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sku_cost_version WHERE variant_id=?",Integer.class,sku)).isEqualTo(1);
        }finally{start.countDown();executor.shutdownNow();executor.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS);jdbc.update("DELETE FROM sku_cost_version WHERE variant_id=?",sku);jdbc.update("DELETE FROM sku_cost_state WHERE variant_id=?",sku);jdbc.update("DELETE FROM audit_event WHERE resource_id=?",sku);jdbc.update("DELETE FROM product_variant WHERE id=?",sku);jdbc.update("DELETE FROM product WHERE id=?",product);}
    }
}
