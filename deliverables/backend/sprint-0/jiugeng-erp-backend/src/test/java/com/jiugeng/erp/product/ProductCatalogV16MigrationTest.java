package com.jiugeng.erp.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ProductCatalogV16MigrationTest {
    @Test
    void upgradesV15ProductsAndWeightsWithoutDiscardingHistoricalAttributes() throws Exception {
        String url = "jdbc:h2:mem:product-v16-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("15").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO product (id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES ('p1','P1','P1','旧产品名','ACTIVE','system','system')");
            statement.executeUpdate("INSERT INTO product_variant (id,product_id,sku_code,sku_code_normalized,variant_name,package_weight,package_weight_unit,status,attribute_signature) VALUES ('v1','p1','S1','S1','公斤 SKU',0.5000,'KG','ACTIVE','sig-1')");
            statement.executeUpdate("INSERT INTO product_variant (id,product_id,sku_code,sku_code_normalized,variant_name,package_weight,package_weight_unit,status,attribute_signature) VALUES ('v2','p1','S2','S2','磅 SKU',1.0000,'LB','DISABLED','sig-2')");
            statement.executeUpdate("INSERT INTO variant_attribute (variant_id,attribute_code,attribute_code_normalized,attribute_value,display_order) VALUES ('v1','COLOR','COLOR','蓝色',0)");
        }

        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT product_name,version FROM product WHERE id='p1'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("product_name")).isEqualTo("旧产品名");
                assertThat(result.getLong("version")).isZero();
            }
            try (var result = statement.executeQuery("SELECT lifecycle_status,package_weight_grams,version FROM product_variant WHERE id='v1'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("lifecycle_status")).isEqualTo("ACTIVE");
                assertThat(result.getBigDecimal("package_weight_grams")).isEqualByComparingTo(new BigDecimal("500.000"));
                assertThat(result.getLong("version")).isZero();
            }
            try (var result = statement.executeQuery("SELECT package_weight_grams FROM product_variant WHERE id='v2'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo(new BigDecimal("453.592"));
            }
            try (var result = statement.executeQuery("SELECT attribute_value FROM variant_attribute WHERE variant_id='v1'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("蓝色");
            }
            statement.executeUpdate("INSERT INTO product_variant (id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES ('v3','p1','S3','S3','无动态属性 SKU','ACTIVE','sig-1')");
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM product_variant WHERE product_id='p1'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(3);
            }
        }
    }
}
