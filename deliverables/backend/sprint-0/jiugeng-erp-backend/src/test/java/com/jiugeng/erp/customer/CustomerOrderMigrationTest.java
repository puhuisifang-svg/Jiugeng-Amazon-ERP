package com.jiugeng.erp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CustomerOrderMigrationTest {
    @Test void addingDecimalQuantitiesPreservesExistingIntRangeAndUnits() throws Exception {
        String url="jdbc:h2:mem:order-precision-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("41").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            s.executeUpdate("INSERT INTO product(id,product_code,product_code_normalized,name_zh,status,created_by,updated_by) VALUES('synthetic-p','SYN-P','SYN-P','合成产品','ACTIVE','test','test')");
            s.executeUpdate("INSERT INTO dashboard_today_shipment(id,ship_date,product_id,product_code_snapshot,product_name_snapshot,quantity,quantity_unit,box_count,latest_ship_at,status,created_by,updated_by) VALUES('synthetic-t',CURRENT_DATE,'synthetic-p','SYN-P','合成产品',2147483647,'SET',1,CURRENT_TIMESTAMP,'ACTIVE','test','test')");
        }
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT quantity,quantity_unit,order_allocation_id FROM dashboard_today_shipment WHERE id='synthetic-t'")) {
                assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("2147483647");assertThat(r.getString(2)).isEqualTo("SET");assertThat(r.getString(3)).isNull();
            }
            s.executeUpdate("UPDATE dashboard_today_shipment SET quantity=1.125,quantity_unit='米' WHERE id='synthetic-t'");
            try(var r=s.executeQuery("SELECT quantity FROM dashboard_today_shipment WHERE id='synthetic-t'")){r.next();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("1.125");}
        }
        assertThat(Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate().migrationsExecuted).isZero();
    }
}
