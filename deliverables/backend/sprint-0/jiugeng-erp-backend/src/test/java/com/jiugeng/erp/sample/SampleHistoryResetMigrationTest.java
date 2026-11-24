package com.jiugeng.erp.sample;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class SampleHistoryResetMigrationTest {
    @Test void deletesOnlyOldSampleBusinessHistory() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE audit_event(id INT, category VARCHAR(32), resource_type VARCHAR(64), action VARCHAR(64))");
            int id = 0;
            for (String action : new String[]{"SAMPLE_CREATE", "SAMPLE_COPY", "SAMPLE_UPDATE", "SAMPLE_REMOVE", "SAMPLE_RESTORE"}) {
                try (var insert = connection.prepareStatement("INSERT INTO audit_event VALUES (?, 'BUSINESS', 'SAMPLE_LOCATION', ?)")) {
                    insert.setInt(1, ++id); insert.setString(2, action); insert.executeUpdate();
                }
            }
            statement.execute("INSERT INTO audit_event VALUES (6,'BUSINESS','PRODUCT','SAMPLE_UPDATE'),(7,'SECURITY','SAMPLE_LOCATION','SAMPLE_UPDATE'),(8,'BUSINESS','SAMPLE_LOCATION','OTHER_ACTION')");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V108__reset_sample_location_history.sql"));
            try (var rows = statement.executeQuery("SELECT id FROM audit_event ORDER BY id")) {
                var remaining = new java.util.ArrayList<Integer>();
                while (rows.next()) remaining.add(rows.getInt(1));
                assertThat(remaining).containsExactly(6, 7, 8);
            }
        }
    }
}
