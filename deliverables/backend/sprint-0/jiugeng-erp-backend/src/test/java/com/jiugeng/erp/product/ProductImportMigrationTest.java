package com.jiugeng.erp.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class ProductImportMigrationTest {
    @Test
    void upgradesLegacyVariantWeightAndAddsDurableImportResults() throws Exception {
        String url="jdbc:h2:mem:product-import-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("9").load().migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()){
            statement.executeUpdate("INSERT INTO product (id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES ('p1','P1','P1','旧产品','ACTIVE','system','system')");
            statement.executeUpdate("INSERT INTO product_variant (id,product_id,sku_code,sku_code_normalized,variant_name,package_weight,status,attribute_signature) VALUES ('v1','p1','S1','S1','旧 SKU',0.5000,'ACTIVE','legacy-signature')");
            statement.executeUpdate("INSERT INTO import_batch (id,import_type,template_version,status,phase,storage_key,file_hash,upload_idempotency_key,upload_payload_hash,actor_user_id,actor_login_name) VALUES ('b1','PRODUCTS','1.0','VALIDATED','WAITING_CONFIRMATION','a.xlsx','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa','key','bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','system','system')");
        }
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()){
            try(var result=statement.executeQuery("SELECT package_weight_unit FROM product_variant WHERE id='v1'")){assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("KG");}
            try(var result=statement.executeQuery("SELECT result_created_rows,result_updated_rows,result_unchanged_rows FROM import_batch WHERE id='b1'")){assertThat(result.next()).isTrue();assertThat(result.getInt(1)).isZero();assertThat(result.getInt(2)).isZero();assertThat(result.getInt(3)).isZero();}
        }
    }
}
