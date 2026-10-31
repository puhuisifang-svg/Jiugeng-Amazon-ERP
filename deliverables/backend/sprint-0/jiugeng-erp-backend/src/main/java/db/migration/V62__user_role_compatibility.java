package db.migration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
public class V62__user_role_compatibility extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        boolean mysql=context.getConnection().getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        try(var sql=context.getConnection().createStatement()) {
            sql.execute("ALTER TABLE iam_user DROP "+(mysql?"CHECK ":"CONSTRAINT ")+"ck_iam_user_role");
            sql.execute("ALTER TABLE iam_user ADD CONSTRAINT ck_iam_user_role CHECK (role IN ('ADMIN','PRODUCT_MATERIAL','SELECTION','CUSTOM'))");
        }
    }
}
