package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** MySQL and H2 use different syntax for dropping a named check constraint. */
public class V28__shipment_dispatch extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 28001; }
    @Override public void migrate(Context context) throws Exception {
        boolean mysql=context.getConnection().getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
        try(var statement=context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE dashboard_today_shipment DROP "+(mysql?"CHECK ":"CONSTRAINT ")+"ck_dashboard_today_shipment_status");
            statement.execute("ALTER TABLE dashboard_today_shipment ADD CONSTRAINT ck_dashboard_today_shipment_status CHECK(status IN ('ACTIVE','ARCHIVED','SHIPPED'))");
            statement.execute("""
                CREATE TABLE dashboard_shipment_dispatch (
                    id VARCHAR(36) PRIMARY KEY,
                    today_shipment_id VARCHAR(36) NOT NULL UNIQUE,
                    product_id VARCHAR(36) NOT NULL,
                    product_code VARCHAR(64) NOT NULL,
                    product_name VARCHAR(128) NOT NULL,
                    sku_codes TEXT NOT NULL,
                    quantity BIGINT NOT NULL,
                    box_count BIGINT NOT NULL,
                    remark VARCHAR(500),
                    planned_date DATE NOT NULL,
                    business_date DATE NOT NULL,
                    dispatched_at TIMESTAMP(6) NOT NULL,
                    actor_id VARCHAR(36) NOT NULL,
                    actor_name VARCHAR(128) NOT NULL,
                    request_key VARCHAR(128) NOT NULL,
                    source_version BIGINT NOT NULL,
                    CONSTRAINT uk_dispatch_request UNIQUE(actor_id,request_key),
                    CONSTRAINT ck_dispatch_amount CHECK(quantity>0 AND box_count>0)
                )
                """);
            statement.execute("CREATE INDEX ix_dispatch_date ON dashboard_shipment_dispatch(business_date,product_id)");
        }
    }
}
