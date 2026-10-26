package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V43__order_shipment_links extends BaseJavaMigration {
    @Override public Integer getChecksum() {return 43001;}
    @Override public void migrate(Context context) throws Exception {
        boolean mysql=context.getConnection().getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
        try(var s=context.getConnection().createStatement()) {
            for(String suffix:java.util.List.of("today_shipment","pending_shipment")) {
                String table="dashboard_"+suffix;
                s.execute("ALTER TABLE "+table+" DROP "+(mysql?"CHECK ":"CONSTRAINT ")+"ck_dashboard_"+suffix+"_unit");
                // Preserve the complete historical signed-INT range while adding fractional units.
                s.execute("ALTER TABLE "+table+" MODIFY quantity DECIMAL(13,3) NOT NULL");
                s.execute("ALTER TABLE "+table+" MODIFY quantity_unit VARCHAR(16) NOT NULL DEFAULT 'SET'");
                s.execute("ALTER TABLE "+table+" ADD COLUMN order_allocation_id VARCHAR(36)");
                s.execute("CREATE INDEX idx_"+suffix+"_allocation ON "+table+"(order_allocation_id)");
            }
            s.execute("ALTER TABLE dashboard_shipment_dispatch MODIFY quantity DECIMAL(13,3) NOT NULL");
            s.execute("ALTER TABLE dashboard_shipment_dispatch ADD COLUMN quantity_unit VARCHAR(16) NOT NULL DEFAULT 'SET'");
            s.execute("ALTER TABLE dashboard_shipment_dispatch ADD COLUMN order_allocation_id VARCHAR(36)");
        }
    }
}
