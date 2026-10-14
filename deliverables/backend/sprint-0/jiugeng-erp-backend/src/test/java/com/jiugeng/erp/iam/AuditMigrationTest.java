package com.jiugeng.erp.iam;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class AuditMigrationTest {
    @Test
    void migratesLegacySecurityEventsIntoTheAppendOnlyAuditTable() throws Exception {
        String url = "jdbc:h2:mem:audit-migration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target("2")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.prepareStatement("""
                        INSERT INTO iam_security_event (
                            id, user_id, login_name, event_type, success, detail, occurred_at
                        ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """)) {
            statement.setString(1, "legacy-event-1");
            statement.setNull(2, java.sql.Types.VARCHAR);
            statement.setString(3, "legacy-user");
            statement.setString(4, "LOGIN_FAILED");
            statement.setBoolean(5, false);
            statement.setString(6, "INVALID_CREDENTIALS");
            statement.executeUpdate();
        }

        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.prepareStatement("""
                        SELECT category, action, actor_login_name, resource_type, resource_id, outcome, detail
                        FROM audit_event WHERE id = ?
                        """)) {
            statement.setString(1, "legacy-event-1");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("category")).isEqualTo("SECURITY");
                assertThat(result.getString("action")).isEqualTo("LOGIN_FAILED");
                assertThat(result.getString("actor_login_name")).isEqualTo("legacy-user");
                assertThat(result.getString("resource_type")).isEqualTo("ACCOUNT");
                assertThat(result.getString("resource_id")).isEqualTo("legacy-user");
                assertThat(result.getString("outcome")).isEqualTo("FAILURE");
                assertThat(result.getString("detail")).isEqualTo("INVALID_CREDENTIALS");
            }
        }

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var result = connection.getMetaData().getTables(null, null, "iam_security_event", null)) {
            assertThat(result.next()).isFalse();
        }
    }
}
