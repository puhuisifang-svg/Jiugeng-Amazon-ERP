package db.migration;

import java.util.*;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Backfill existing roles; future edits use the permission dependency catalog. */
public class V106__purchase_permission_dependencies extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        Map<String, Set<String>> roles = new LinkedHashMap<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT role_id,permission_code FROM iam_role_permission")) {
            while (rows.next()) roles.computeIfAbsent(rows.getString(1), key -> new HashSet<>()).add(rows.getString(2));
        }
        for (var entry : roles.entrySet()) {
            var desired = new HashSet<>(entry.getValue());
            if (desired.stream().anyMatch(p -> p.startsWith("purchases."))) desired.addAll(List.of("purchases.read","products.read","materials.read","suppliers.read","inventory.read"));
            desired.removeAll(entry.getValue());
            if (desired.isEmpty()) continue;
            try (var insert = connection.prepareStatement("INSERT INTO iam_role_permission(role_id,permission_code) VALUES (?,?)")) {
                for (var code : desired) { insert.setString(1,entry.getKey()); insert.setString(2,code); insert.addBatch(); }
                insert.executeBatch();
            }
            try (var update = connection.prepareStatement("UPDATE iam_role SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
                update.setString(1,entry.getKey()); update.executeUpdate();
            }
            try (var update = connection.prepareStatement("UPDATE iam_user SET authorization_version=authorization_version+1 WHERE id IN (SELECT user_id FROM iam_user_role WHERE role_id=?)")) {
                update.setString(1,entry.getKey()); update.executeUpdate();
            }
        }
    }
}
