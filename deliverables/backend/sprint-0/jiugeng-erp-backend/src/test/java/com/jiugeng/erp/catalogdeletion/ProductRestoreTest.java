package com.jiugeng.erp.catalogdeletion;

import static org.assertj.core.api.Assertions.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest @ActiveProfiles("test") @Transactional
class ProductRestoreTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogDeletionService deletion;
    @Autowired CatalogRestoreService restore;
    final AuthenticatedActor actor = new AuthenticatedActor() {
        public String userId() { return "synthetic-restore"; }
        public String loginName() { return "synthetic-restore"; }
        public String displayName() { return "合成恢复操作人"; }
    };
    record Fixture(String parent, String child) {}
    Fixture fixture() {
        String parent=UUID.randomUUID().toString(),child=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO product(id,product_code,product_code_normalized,product_name,name_zh,status,created_by,updated_by) VALUES(?,?,?,'合成恢复父体','合成恢复父体','ACTIVE','test','test')",parent,parent,parent);
        jdbc.update("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,lifecycle_status,attribute_signature) VALUES(?,?,?,?,'合成恢复子体','ACTIVE','ACTIVE',?)",child,parent,child,child,child);
        return new Fixture(parent,child);
    }
    @Test void requiresZeroStockBeforeDeletionAndPreservesTheBalanceRecord() {
        var f=fixture();
        jdbc.update("INSERT INTO product_inventory_balance(variant_id,quantity) VALUES(?,3)",f.child());
        assertThatThrownBy(()->deletion.product(f.parent(),f.child(),0,0L,actor)).isInstanceOf(CatalogDeletionService.Failure.class).hasMessageContaining("仍有库存");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE id=? AND deleted_at IS NULL",Integer.class,f.child())).isEqualTo(1);
        jdbc.update("UPDATE product_inventory_balance SET quantity=0 WHERE variant_id=?",f.child());
        deletion.product(f.parent(),f.child(),0,0L,actor);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_inventory_balance WHERE variant_id=?",Integer.class,f.child())).isEqualTo(1);
    }
    @Test void restoresParentBeforeChildAndRejectsStaleAndRepeatedRestores() {
        var f=fixture();
        deletion.product(f.parent(),f.child(),0,0L,actor);
        deletion.product(f.parent(),null,1,null,actor);
        assertThatThrownBy(()->restore.restore("SKU",f.child(),1,actor)).hasMessageContaining("先恢复所属父体");
        assertThatThrownBy(()->restore.restore("PRODUCT",f.parent(),0,actor)).hasMessageContaining("记录已变化");
        restore.restore("PRODUCT",f.parent(),2,actor);
        assertThat(jdbc.queryForObject("SELECT version FROM product WHERE id=?",Long.class,f.parent())).isEqualTo(3);
        restore.restore("SKU",f.child(),1,actor);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variant WHERE id=? AND deleted_at IS NULL",Integer.class,f.child())).isEqualTo(1);
        assertThatThrownBy(()->restore.restore("SKU",f.child(),1,actor)).hasMessageContaining("记录已恢复");
        assertThat(restore.deleted(0,100).items()).noneMatch(row->row.id().equals(f.parent())||row.id().equals(f.child()));
    }
}
