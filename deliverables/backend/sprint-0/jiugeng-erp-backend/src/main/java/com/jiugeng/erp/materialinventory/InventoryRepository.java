package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.InventoryTypes.BalanceView;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionType;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionView;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryRepository {
    static final String WAREHOUSE_ID = "00000000-0000-0000-0000-000000000001";
    private final JdbcClient jdbc;
    InventoryRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    void lockWarehouse() {
        jdbc.sql("SELECT id FROM warehouse WHERE id=:id AND enabled=TRUE FOR UPDATE")
                .param("id", WAREHOUSE_ID).query(String.class).single();
    }

    MaterialRef activeMaterial(String id) {
        return jdbc.sql("SELECT id, material_code, name, unit FROM auxiliary_material WHERE id=:id AND status='ACTIVE'")
                .param("id", id).query((rs, row) -> new MaterialRef(rs.getString("id"), rs.getString("material_code"),
                        rs.getString("name"), rs.getString("unit"))).optional()
                .orElseThrow(() -> new MaterialException(409, "ACTIVE_MATERIAL_REQUIRED", "只能对启用辅料记账"));
    }

    void ensureBalance(MaterialRef material) {
        int count = jdbc.sql("SELECT COUNT(*) FROM inventory_balance WHERE warehouse_id=:warehouse AND auxiliary_material_id=:material")
                .param("warehouse", WAREHOUSE_ID).param("material", material.id()).query(Integer.class).single();
        if (count == 0) jdbc.sql("""
                INSERT INTO inventory_balance (warehouse_id, auxiliary_material_id, quantity, safety_stock, unit)
                VALUES (:warehouse, :material, 0, 0, :unit)
                """).param("warehouse", WAREHOUSE_ID).param("material", material.id())
                .param("unit", material.unit()).update();
    }

    BalanceState lockBalance(String materialId) {
        return jdbc.sql("""
                SELECT quantity, safety_stock, unit, opening_transaction_id FROM inventory_balance
                WHERE warehouse_id=:warehouse AND auxiliary_material_id=:material FOR UPDATE
                """).param("warehouse", WAREHOUSE_ID).param("material", materialId)
                .query((rs, row) -> new BalanceState(rs.getBigDecimal("quantity"), rs.getBigDecimal("safety_stock"),
                        rs.getString("unit"), rs.getString("opening_transaction_id"))).single();
    }

    void updateBalance(String materialId, BigDecimal quantity, String unit) {
        jdbc.sql("""
                UPDATE inventory_balance SET quantity=:quantity, unit=:unit, updated_at=CURRENT_TIMESTAMP
                WHERE warehouse_id=:warehouse AND auxiliary_material_id=:material
                """).param("quantity", quantity).param("unit", unit).param("warehouse", WAREHOUSE_ID)
                .param("material", materialId).update();
    }

    void updateSafetyStock(String materialId, BigDecimal safetyStock) {
        jdbc.sql("""
                UPDATE inventory_balance SET safety_stock=:safetyStock, updated_at=CURRENT_TIMESTAMP
                WHERE warehouse_id=:warehouse AND auxiliary_material_id=:material
                """).param("safetyStock", safetyStock).param("warehouse", WAREHOUSE_ID)
                .param("material", materialId).update();
    }

    void markOpening(String materialId, String transactionId, BigDecimal safetyStock) {
        jdbc.sql("""
                UPDATE inventory_balance SET opening_transaction_id=:transactionId, safety_stock=:safetyStock,
                    updated_at=CURRENT_TIMESTAMP WHERE warehouse_id=:warehouse AND auxiliary_material_id=:material
                """).param("transactionId", transactionId).param("safetyStock", safetyStock)
                .param("warehouse", WAREHOUSE_ID).param("material", materialId).update();
    }

    List<BalanceView> balances() {
        return jdbc.sql("""
                SELECT w.warehouse_code, b.auxiliary_material_id, m.material_code, m.name, b.quantity,
                       b.safety_stock, b.unit, b.updated_at
                FROM inventory_balance b JOIN warehouse w ON w.id=b.warehouse_id
                JOIN auxiliary_material m ON m.id=b.auxiliary_material_id
                ORDER BY m.material_code_normalized
                """).query(this::mapBalance).list();
    }

    Optional<BalanceView> balance(String materialId) {
        return jdbc.sql("""
                SELECT w.warehouse_code, b.auxiliary_material_id, m.material_code, m.name, b.quantity,
                       b.safety_stock, b.unit, b.updated_at
                FROM inventory_balance b JOIN warehouse w ON w.id=b.warehouse_id
                JOIN auxiliary_material m ON m.id=b.auxiliary_material_id
                WHERE b.warehouse_id=:warehouse AND b.auxiliary_material_id=:material
                """).param("warehouse", WAREHOUSE_ID).param("material", materialId).query(this::mapBalance).optional();
    }

    Optional<TransactionView> transactionByRequest(String requestId) { return transaction("t.request_id=:value", requestId); }
    Optional<TransactionView> transactionById(String id) { return transaction("t.id=:value", id); }
    boolean isReversed(String id) {
        return jdbc.sql("SELECT COUNT(*) FROM inventory_transaction WHERE reference_transaction_id=:id")
                .param("id", id).query(Integer.class).single() > 0;
    }

    private Optional<TransactionView> transaction(String condition, String value) {
        return jdbc.sql(transactionSelect() + " WHERE " + condition).param("value", value).query(this::mapTransaction).optional();
    }

    List<TransactionView> transactions(String materialId) {
        String condition = materialId == null || materialId.isBlank() ? "" : " WHERE t.auxiliary_material_id=:material";
        var query = jdbc.sql(transactionSelect() + condition + " ORDER BY t.occurred_at DESC, t.id DESC");
        if (!condition.isEmpty()) query = query.param("material", materialId.trim());
        return query.query(this::mapTransaction).list();
    }

    void insertTransaction(String id, String requestId, String hash, String materialId, TransactionType type,
            BigDecimal quantity, BigDecimal signedQuantity, String unit, String reason, String referenceId,
            BigDecimal balanceAfter, String actorId, String actorLogin) {
        jdbc.sql("""
                INSERT INTO inventory_transaction (id, request_id, payload_hash, warehouse_id,
                    auxiliary_material_id, transaction_type, quantity, signed_quantity, unit, reason,
                    reference_transaction_id, balance_after, actor_user_id, actor_login_name)
                VALUES (:id, :requestId, :hash, :warehouse, :material, :type, :quantity, :signedQuantity,
                    :unit, :reason, :referenceId, :balanceAfter, :actorId, :actorLogin)
                """).param("id", id).param("requestId", requestId).param("hash", hash)
                .param("warehouse", WAREHOUSE_ID).param("material", materialId).param("type", type.name())
                .param("quantity", quantity).param("signedQuantity", signedQuantity).param("unit", unit)
                .param("reason", reason).param("referenceId", referenceId).param("balanceAfter", balanceAfter)
                .param("actorId", actorId).param("actorLogin", actorLogin).update();
    }

    String payloadHash(String requestId) {
        return jdbc.sql("SELECT payload_hash FROM inventory_transaction WHERE request_id=:id")
                .param("id", requestId).query(String.class).optional().orElse(null);
    }

    private BalanceView mapBalance(ResultSet rs, int row) throws SQLException {
        BigDecimal quantity = rs.getBigDecimal("quantity");
        BigDecimal safety = rs.getBigDecimal("safety_stock");
        return new BalanceView(rs.getString("warehouse_code"), rs.getString("auxiliary_material_id"),
                rs.getString("material_code"), rs.getString("name"), quantity, safety, rs.getString("unit"),
                quantity.compareTo(safety) < 0, instant(rs, "updated_at"));
    }

    private String transactionSelect() {
        return """
                SELECT t.id, t.request_id, w.warehouse_code, t.auxiliary_material_id, m.material_code,
                       m.name AS material_name, t.transaction_type, t.quantity, t.signed_quantity, t.unit,
                       t.reason, t.reference_transaction_id, t.balance_after, t.actor_login_name, t.occurred_at
                FROM inventory_transaction t JOIN warehouse w ON w.id=t.warehouse_id
                JOIN auxiliary_material m ON m.id=t.auxiliary_material_id
                """;
    }

    private TransactionView mapTransaction(ResultSet rs, int row) throws SQLException {
        return new TransactionView(rs.getString("id"), rs.getString("request_id"), rs.getString("warehouse_code"),
                rs.getString("auxiliary_material_id"), rs.getString("material_code"), rs.getString("material_name"),
                TransactionType.valueOf(rs.getString("transaction_type")), rs.getBigDecimal("quantity"),
                rs.getBigDecimal("signed_quantity"), rs.getString("unit"), rs.getString("reason"),
                rs.getString("reference_transaction_id"), rs.getBigDecimal("balance_after"),
                rs.getString("actor_login_name"), instant(rs, "occurred_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
    record MaterialRef(String id, String code, String name, String unit) {}
    record BalanceState(BigDecimal quantity, BigDecimal safetyStock, String unit, String openingTransactionId) {}
}
