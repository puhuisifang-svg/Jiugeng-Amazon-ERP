package com.jiugeng.erp.development;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class DevelopmentMigrationTest {
    @Test void backfillsOnlyApprovedHistoricalCandidatesExactlyOnce() throws Exception {
        String url="jdbc:h2:mem:development-v25;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target("24").load().migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement()) {
            for(String status:new String[]{"NEW","REVIEWING","APPROVED","REJECTED"})
                s.executeUpdate("INSERT INTO selection_candidate(id,asin,asin_normalized,title,status,score,source_url,source,created_by,updated_by) VALUES ('"+status+"','"+status+"','"+status+"','历史候选','"+status+"',88,'https://www.amazon.com/dp/B0DEV33001','MANUAL','test','test')");
        }
        var flyway=Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load();flyway.migrate();flyway.migrate();
        try(var c=DriverManager.getConnection(url,"sa","");var s=c.createStatement();var r=s.executeQuery("SELECT * FROM product_development")) {
            assertThat(r.next()).isTrue();assertThat(r.getString("candidate_id")).isEqualTo("APPROVED");
            assertThat(r.getString("status")).isEqualTo("DRAFT");assertThat(r.getString("source_title")).isEqualTo("历史候选");
            assertThat(r.getString("source_type")).isEqualTo("CANDIDATE");assertThat(r.getString("manual_request_id")).isNull();
            assertThat(r.getInt("source_score")).isEqualTo(88);assertThat(r.next()).isFalse();
        }
    }
}
