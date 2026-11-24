package com.jiugeng.erp.supplier;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class SupplierHistoryClearMigrationTest {
    @Test void deletesOnlySupplierHistoryAndPreservesMaterialAndSecurityEvents() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE audit_event(id INT, category VARCHAR(32), resource_type VARCHAR(64), action VARCHAR(64))");
            int id = 0;
            for (String action : new String[]{"SUPPLIER_CREATE", "SUPPLIER_UPDATE", "SUPPLIER_DELETE", "SUPPLIER_ARCHIVED_LINK_REMOVE"}) {
                try (var insert = connection.prepareStatement("INSERT INTO audit_event VALUES (?, 'BUSINESS', 'SUPPLIER', ?)")) {
                    insert.setInt(1, ++id); insert.setString(2, action); insert.executeUpdate();
                }
            }
            statement.execute("INSERT INTO audit_event VALUES (6,'BUSINESS','PRODUCT','SUPPLIER_UPDATE'),(7,'SECURITY','SUPPLIER','SUPPLIER_UPDATE'),(8,'BUSINESS','SUPPLIER','MATERIAL_SUPPLIERS_REPLACE')");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V109__clear_supplier_change_history.sql"));
            try (var rows = statement.executeQuery("SELECT id FROM audit_event ORDER BY id")) {
                var remaining = new java.util.ArrayList<Integer>();
                while (rows.next()) remaining.add(rows.getInt(1));
                assertThat(remaining).containsExactly(6, 7, 8);
            }
        }
    }
}
