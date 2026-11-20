package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Preserve legacy books until their customer ownership is explicitly confirmed. */
public class V94__finance_customer_subjects extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        boolean mysql=context.getConnection().getMetaData().getDatabaseProductName().equalsIgnoreCase("MySQL");
        try(var s=context.getConnection().createStatement()) {
            s.execute(mysql?"ALTER TABLE finance_bill MODIFY shop_id VARCHAR(36) NULL":"ALTER TABLE finance_bill ALTER COLUMN shop_id DROP NOT NULL");
            s.execute("ALTER TABLE finance_bill ADD customer_id VARCHAR(36) NULL");
            s.execute("ALTER TABLE finance_bill ADD CONSTRAINT fk_finance_bill_customer FOREIGN KEY(customer_id) REFERENCES customer(id)");
            s.execute("CREATE INDEX idx_finance_customer_date ON finance_bill(customer_id,voided,bill_date,id)");
            s.execute("CREATE TABLE finance_customer_account (id VARCHAR(36) PRIMARY KEY,customer_id VARCHAR(36) NOT NULL,name VARCHAR(100) NOT NULL,name_key VARCHAR(100) NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,version BIGINT NOT NULL DEFAULT 0,CONSTRAINT fk_finance_account_customer FOREIGN KEY(customer_id) REFERENCES customer(id),CONSTRAINT uq_finance_customer_account UNIQUE(customer_id,name_key))");
        }
    }
}
