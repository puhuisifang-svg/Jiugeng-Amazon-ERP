package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Retain historical identities while allowing a new quotation for the same customer and SKU. */
public class V37__customer_product_logical_deletion extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 37001; }
    @Override public void migrate(Context context) throws Exception {
        var db=context.getConnection();
        try(var s=db.createStatement()) {
            s.execute("ALTER TABLE customer_product ADD COLUMN deleted_at TIMESTAMP NULL");
            s.execute("ALTER TABLE customer_product ADD COLUMN deleted_by VARCHAR(36) NULL");
            s.execute("ALTER TABLE customer_product ADD COLUMN deletion_key VARCHAR(36) NOT NULL DEFAULT ''");
            s.execute("ALTER TABLE customer_product ADD CONSTRAINT uk_customer_product_live_sku UNIQUE(customer_id,variant_id,deletion_key)");
            if(db.getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL"))
                s.execute("ALTER TABLE customer_product DROP INDEX uk_customer_product_sku");
            else s.execute("ALTER TABLE customer_product DROP CONSTRAINT uk_customer_product_sku");
        }
    }
}
