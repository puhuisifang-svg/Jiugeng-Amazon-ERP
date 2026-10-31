package db.migration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
public class V56__candidate_optional_identity extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        boolean mysql=context.getConnection().getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
        try(var s=context.getConnection().createStatement()) {
            s.execute(mysql?"ALTER TABLE product_development DROP CHECK ck_development_source":"ALTER TABLE product_development DROP CONSTRAINT ck_development_source");
            s.execute("ALTER TABLE product_development ADD CONSTRAINT ck_development_source CHECK ((source_type='CANDIDATE' AND candidate_id IS NOT NULL AND source_title IS NOT NULL AND manual_request_id IS NULL) OR (source_type='MANUAL' AND candidate_id IS NULL AND manual_request_id IS NOT NULL AND manual_actor_id IS NOT NULL AND manual_payload_hash IS NOT NULL))");
            for(String column:new String[]{"asin","asin_normalized"})
                s.execute(mysql?"ALTER TABLE selection_candidate MODIFY "+column+" VARCHAR(16) NULL":"ALTER TABLE selection_candidate ALTER COLUMN "+column+" DROP NOT NULL");
        }
    }
}
