package com.jiugeng.erp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CustomerShippingMigrationTest {
    @Test void backfillsOnlyOpenSkuBoundShipmentsAndDoesNotInventPrices() throws Exception {
        String url="jdbc:h2:mem:customer-shipping-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("30").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES('p1','P1','P1','合成父体','ACTIVE','test','test')");
            s.executeUpdate("INSERT INTO product_variant(id,product_id,sku_code,sku_code_normalized,variant_name,status,attribute_signature) VALUES('v1','p1','S1','S1','合成SKU','ACTIVE','v1')");
            for(String status:new String[]{"ACTIVE","SHIPPED","ARCHIVED"}) {
                s.executeUpdate("INSERT INTO dashboard_today_shipment(id,ship_date,product_id,product_code_snapshot,product_name_snapshot,quantity,quantity_unit,box_count,latest_ship_at,status,created_by,updated_by,variant_id) VALUES('"+status+"',CURRENT_DATE,'p1','S1','合成SKU',2,'SET',1,CURRENT_TIMESTAMP,'"+status+"','test','test','v1')");
            }
            s.executeUpdate("INSERT INTO dashboard_pending_shipment(id,planned_ship_date,product_id,product_code_snapshot,product_name_snapshot,quantity,quantity_unit,box_count,latest_ship_at,status,created_by,updated_by,variant_id) VALUES('pending',CURRENT_DATE,'p1','S1','合成SKU',2,'SET',1,CURRENT_TIMESTAMP,'PENDING','test','test','v1')");
        }
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT c.name,c.status FROM customer_preferences p JOIN customer c ON c.id=p.default_customer_id")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("九更");assertThat(r.getString(2)).isEqualTo("ACTIVE");}
            String customerProduct;
            try(var r=s.executeQuery("SELECT id,supply_price FROM customer_product WHERE variant_id='v1'")){assertThat(r.next()).isTrue();customerProduct=r.getString(1);assertThat(r.getBigDecimal(2)).isNull();assertThat(r.next()).isFalse();}
            try(var r=s.executeQuery("SELECT customer_product_id,customer_snapshot,version FROM dashboard_today_shipment WHERE id='ACTIVE'")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo(customerProduct);assertThat(r.getString(2)).contains("九更");assertThat(r.getLong(3)).isEqualTo(1);}
            try(var r=s.executeQuery("SELECT customer_product_id FROM dashboard_pending_shipment WHERE id='pending'")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo(customerProduct);}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM dashboard_today_shipment WHERE status IN ('SHIPPED','ARCHIVED') AND customer_product_id IS NULL AND version=0")){r.next();assertThat(r.getInt(1)).isEqualTo(2);}
            try(var r=s.executeQuery("SELECT COUNT(*) FROM audit_event WHERE action='CUSTOMER_SHIPMENT_BACKFILL'")){r.next();assertThat(r.getInt(1)).isEqualTo(2);}
        }
        // A normal restart never repeats the one-time assignment or creates duplicate customer products.
        assertThat(Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate().migrationsExecuted).isZero();
    }
}
