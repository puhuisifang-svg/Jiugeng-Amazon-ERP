package com.jiugeng.erp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CustomerCustomsMigrationTest {
    @Test void copiesLegacyDataToEveryCustomerOnceAndPreservesIndependentEdits() throws Exception {
        String url="jdbc:h2:mem:customer-customs-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("96").load().migrate();
        String profile="{\"nameZh\":\"合成申报品名\",\"chinaCode\":\"6307909000\",\"elements\":{\"brandType\":\"无品牌\"},\"state\":\"DRAFT\",\"version\":7}";
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES('p1','P1','P1','合成父体','ACTIVE','test','test')");
            s.executeUpdate("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature,customs_data,customs_version) VALUES('v1','p1','S1','S1','合成商品','ACTIVE','v1','"+profile+"',7)");
            for(String id:new String[]{"c1","c2"}){
                s.executeUpdate("INSERT INTO customer(id,name,status,created_by,updated_by) VALUES('"+id+"','合成客户','ACTIVE','test','test')");
                s.executeUpdate("INSERT INTO customer_product(id,customer_id,product_id,variant_id,sku_code,product_name,price_unit,status,created_by,updated_by) VALUES('"+id+"','"+id+"','p1','v1','S1','合成商品','件','ACTIVE','test','test')");
            }
        }
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT customs_data FROM customer_product WHERE id IN ('c1','c2')")){int count=0;while(r.next()){assertThat(r.getString(1)).isEqualTo(profile);count++;}assertThat(count).isEqualTo(2);}
            s.executeUpdate("UPDATE customer_product SET customs_data=NULL WHERE id='c1'");
        }
        assertThat(Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate().migrationsExecuted).isZero();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT customs_data FROM customer_product WHERE id='c1'")){r.next();assertThat(r.getString(1)).isNull();}
            try(var r=s.executeQuery("SELECT customs_data FROM customer_product WHERE id='c2'")){r.next();assertThat(r.getString(1)).isEqualTo(profile);}
            try(var r=s.executeQuery("SELECT customs_data FROM product_variant WHERE id='v1'")){r.next();assertThat(r.getString(1)).isEqualTo(profile);}
        }
    }
}
