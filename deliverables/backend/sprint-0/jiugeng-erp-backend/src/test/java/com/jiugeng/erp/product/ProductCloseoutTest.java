package com.jiugeng.erp.product;

import static org.assertj.core.api.Assertions.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class ProductCloseoutTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductInventoryService inventory;
    @Autowired jakarta.validation.Validator validator;
    @Autowired ProductPageService pages;
    @Autowired ProductService products;
    @Autowired ProductInventoryController inventoryController;
    final AuthenticatedActor actor=new AuthenticatedActor(){public String userId(){return "synthetic-closeout";}public String loginName(){return "synthetic-closeout";}public String displayName(){return "合成操作人";}};
    record Fixture(String parent,String first,String second,String name){}
    Fixture fixture(){String p=UUID.randomUUID().toString(),a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString(),name="收尾合成"+p;
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,product_name,name_zh,status,created_by,updated_by) VALUES(?,?,?,?,?,'ACTIVE','test','test')",p,p,p,name,name);
        for(String id:List.of(a,b))jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,lifecycle_status,attribute_signature) VALUES(?,?,?,?,?,'ACTIVE','ACTIVE',?)",id,p,id,id,id.equals(a)?"合成甲":"合成乙",id);
        return new Fixture(p,a,b,name);
    }
    @Test void inventoryControllerRequiresTheMatchingMovementPermission() {
        var context=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var previous=context.getAuthentication();
        try {
            context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor,"unused",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("products.read"),new org.springframework.security.core.authority.SimpleGrantedAuthority("inventory.inbound"))));
            var f=fixture();
            var inbound=new ProductInventoryController.Command("permission-in","INBOUND",BigDecimal.ONE,"合成权限入库");
            assertThat(inventoryController.transact(f.first(),inbound,actor).item().balanceAfter()).isEqualByComparingTo("1");
            var outbound=new ProductInventoryController.Command("permission-out","OUTBOUND",BigDecimal.ONE,"合成权限出库");
            assertThatThrownBy(()->inventoryController.transact(f.first(),outbound,actor)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally { context.setAuthentication(previous); }
    }
    @Test void inventoryRetriesAreIdempotentAndCannotOverdraw(){
        var f=fixture();var first=inventory.transact(f.first(),"same-request","INBOUND",new BigDecimal("5"),"合成入库",actor);
        assertThat(first.replayed()).isFalse();assertThat(inventory.transact(f.first(),"same-request","INBOUND",new BigDecimal("5"),"合成入库",actor).replayed()).isTrue();
        assertThat(inventory.balances(List.of(f.first())).getFirst().quantity()).isEqualByComparingTo("5");
        assertThatThrownBy(()->inventory.transact(f.first(),"same-request","INBOUND",new BigDecimal("6"),"合成入库",actor)).isInstanceOf(ProductException.class).hasMessageContaining("不同库存操作");
        assertThatThrownBy(()->inventory.transact(f.first(),"out","OUTBOUND",new BigDecimal("6"),"合成出库",actor)).isInstanceOf(ProductException.class).hasMessageContaining("库存不足");
        assertThat(inventory.history(f.first(),0,20).total()).isEqualTo(1);
        inventory.transact(f.first(),"out-ok","OUTBOUND",new BigDecimal("2"),"合成出库",actor);
        assertThat(inventory.balances(List.of(f.first())).getFirst().quantity()).isEqualByComparingTo("3");
    }
    @Test void inventoryReasonIsOptionalButBoundedAndRetriesRemainIdempotent(){
        var f=fixture();
        var command=new ProductInventoryController.Command("optional-reason","INBOUND",BigDecimal.ONE,null);
        assertThat(validator.validate(command)).isEmpty();
        assertThat(validator.validate(new ProductInventoryController.Command("long-reason","INBOUND",BigDecimal.ONE,"x".repeat(501)))).isNotEmpty();
        assertThat(inventory.transact(f.first(),command.requestId(),command.type(),command.quantity(),command.reason(),actor).item().reason()).isEmpty();
        assertThat(inventory.transact(f.first(),"optional-reason","INBOUND",BigDecimal.ONE,"  ",actor).replayed()).isTrue();
        assertThatThrownBy(()->inventory.transact(f.first(),"optional-reason","INBOUND",BigDecimal.ONE,"different",actor)).isInstanceOf(ProductException.class);
        assertThatThrownBy(()->inventory.transact(f.first(),"long-reason","INBOUND",BigDecimal.ONE,"x".repeat(501),actor)).isInstanceOf(ProductException.class);
        inventory.transact(f.first(),"optional-outbound","OUTBOUND",BigDecimal.ONE,"",actor);
        assertThat(inventory.balances(List.of(f.first())).getFirst().quantity()).isEqualByComparingTo("0");
        assertThat(inventory.history(f.first(),0,20).total()).isEqualTo(2);
    }
    @Test void filteredParentsKeepAllChildrenForInventoryTotals(){
        var f=fixture();inventory.transact(f.first(),"a","INBOUND",new BigDecimal("3"),"合成",actor);inventory.transact(f.second(),"b","INBOUND",new BigDecimal("7"),"合成",actor);
        var filter=new ProductPageService.Filter(f.name(),"PRODUCT_NAME",true,"","","","","","","","",null);
        var result=pages.page(filter,"sku","asc",0,1,true,true);
        assertThat(result.totalCount()).isEqualTo(1);assertThat(result.matchingSkuCount()).isEqualTo(2);assertThat(result.items().getFirst().variants()).hasSize(2);
        assertThat(result.balances().stream().map(ProductInventoryService.Balance::quantity).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("10");
        var selected=new ProductPageService.Filter("","KEYWORD",false,"","","","","","","","",List.of(f.first()));
        var partial=pages.page(selected,"sku","asc",0,1,true,true);
        assertThat(partial.matchingVariantIds()).containsExactly(f.first());assertThat(partial.items().getFirst().variants()).hasSize(2);
    }
    @Test void singleSkuUpdateDoesNotRewriteSiblingAndRejectsStaleVersion(){
        var f=fixture();var current=products.product(f.parent(),true).variants().stream().filter(v->v.id().equals(f.first())).findFirst().orElseThrow();
        var input=new ProductTypes.VariantInput(current.id(),current.skuCode(),"修改合成甲",null,null,null,null,null,null,null,null,null,ProductTypes.VariantStatus.ACTIVE,null,null,current.version(),List.of(),List.of());
        products.updateSku(f.parent(),f.first(),input,actor,true);
        assertThat(jdbc.queryForObject("SELECT version FROM product_variant WHERE id=?",Long.class,f.second())).isZero();
        assertThat(jdbc.queryForObject("SELECT variant_name FROM product_variant WHERE id=?",String.class,f.first())).isEqualTo("修改合成甲");
        assertThatThrownBy(()->products.updateSku(f.parent(),f.first(),input,actor,true)).isInstanceOf(ProductException.class).hasMessageContaining("已被其他用户更新");
    }
}
