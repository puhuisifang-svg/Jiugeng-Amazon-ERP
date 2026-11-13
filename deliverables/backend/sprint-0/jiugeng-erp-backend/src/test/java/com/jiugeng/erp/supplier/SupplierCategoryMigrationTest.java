package com.jiugeng.erp.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class SupplierCategoryMigrationTest {
    @Test void infersBothCategoriesAndPreservesUnclassifiedSuppliers() throws Exception {
        try(var c=DriverManager.getConnection("jdbc:h2:mem:supplier-category-migration;MODE=MySQL", "sa", "");var s=c.createStatement()) {
            s.execute("CREATE TABLE supplier(id VARCHAR(36) PRIMARY KEY,name VARCHAR(128))");
            s.execute("CREATE TABLE product_variant_supplier(supplier_id VARCHAR(36))");
            s.execute("CREATE TABLE auxiliary_material_supplier(supplier_id VARCHAR(36))");
            s.execute("CREATE TABLE purchase_order(supplier_id VARCHAR(36),order_type VARCHAR(20))");
            s.execute("INSERT INTO supplier VALUES('p','产品'),('m','辅料'),('b','两类'),('u','待分类')");
            s.execute("INSERT INTO product_variant_supplier VALUES('p'),('b')");
            s.execute("INSERT INTO auxiliary_material_supplier VALUES('m')");
            s.execute("INSERT INTO purchase_order VALUES('b','MATERIAL')");
            ScriptUtils.executeSqlScript(c,new ClassPathResource("db/migration/V90__supplier_categories.sql"));
            var result=new java.util.HashMap<String,String>();
            try(var rows=s.executeQuery("SELECT id,categories FROM supplier")){while(rows.next())result.put(rows.getString(1),rows.getString(2));}
            assertThat(result).containsEntry("p","PRODUCT").containsEntry("m","MATERIAL").containsEntry("b","MATERIAL,PRODUCT").containsEntry("u","");
        }
    }
}
