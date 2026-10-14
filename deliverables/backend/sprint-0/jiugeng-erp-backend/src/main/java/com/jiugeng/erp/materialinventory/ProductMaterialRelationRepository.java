package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.ProductMaterialRelationTypes.RelationView;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProductMaterialRelationRepository {
    private final JdbcClient jdbc;

    ProductMaterialRelationRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    List<RelationView> findByProduct(String productId) {
        return query("r.product_id = :value", productId);
    }

    List<RelationView> findByVariant(String variantId) {
        return query("r.variant_id = :value", variantId);
    }

    Optional<RelationView> findById(String id) {
        return jdbc.sql(select() + " WHERE r.id = :id").param("id", id).query(this::map).optional();
    }

    private List<RelationView> query(String condition, String value) {
        return jdbc.sql(select() + " WHERE " + condition + " ORDER BY v.sku_code_normalized, m.material_code_normalized")
                .param("value", value).query(this::map).list();
    }

    boolean activeProduct(String id) {
        return count("SELECT COUNT(*) FROM product WHERE id = :id AND status = 'ACTIVE'", id);
    }

    boolean activeVariantInProduct(String variantId, String productId) {
        return jdbc.sql("SELECT COUNT(*) FROM product_variant WHERE id = :id AND product_id = :productId AND status = 'ACTIVE'")
                .param("id", variantId).param("productId", productId).query(Integer.class).single() > 0;
    }

    boolean activeMaterial(String id) {
        return count("SELECT COUNT(*) FROM auxiliary_material WHERE id = :id AND status = 'ACTIVE'", id);
    }

    List<String> activeVariantIds(String productId) {
        return jdbc.sql("SELECT id FROM product_variant WHERE product_id = :productId AND status = 'ACTIVE' ORDER BY sku_code_normalized")
                .param("productId", productId).query(String.class).list();
    }

    boolean exists(String variantId, String materialId, String excludingId) {
        String sql = "SELECT COUNT(*) FROM product_auxiliary_material WHERE variant_id=:variantId AND auxiliary_material_id=:materialId"
                + (excludingId == null ? "" : " AND id<>:id");
        var query = jdbc.sql(sql).param("variantId", variantId).param("materialId", materialId);
        if (excludingId != null) query = query.param("id", excludingId);
        return query.query(Integer.class).single() > 0;
    }

    void insert(String id, String productId, String variantId, String materialId, BigDecimal quantity, String unit,
            BigDecimal lossRate, String remark, String actorId) {
        jdbc.sql("""
                INSERT INTO product_auxiliary_material (id, product_id, variant_id, auxiliary_material_id,
                    standard_quantity, unit, loss_rate, remark, enabled, created_by, updated_by)
                VALUES (:id, :productId, :variantId, :materialId, :quantity, :unit, :lossRate, :remark,
                    TRUE, :actorId, :actorId)
                """).param("id", id).param("productId", productId).param("variantId", variantId)
                .param("materialId", materialId).param("quantity", quantity).param("unit", unit)
                .param("lossRate", lossRate).param("remark", remark).param("actorId", actorId).update();
    }

    void update(String id, BigDecimal quantity, String unit, BigDecimal lossRate, String remark, boolean enabled,
            String actorId) {
        jdbc.sql("""
                UPDATE product_auxiliary_material SET standard_quantity=:quantity, unit=:unit, loss_rate=:lossRate,
                    remark=:remark, enabled=:enabled, updated_by=:actorId, updated_at=CURRENT_TIMESTAMP WHERE id=:id
                """).param("id", id).param("quantity", quantity).param("unit", unit).param("lossRate", lossRate)
                .param("remark", remark).param("enabled", enabled).param("actorId", actorId).update();
    }

    void disable(String id, String actorId) {
        jdbc.sql("UPDATE product_auxiliary_material SET enabled=FALSE, updated_by=:actorId, updated_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("id", id).param("actorId", actorId).update();
    }

    private boolean count(String sql, String id) {
        return jdbc.sql(sql).param("id", id).query(Integer.class).single() > 0;
    }

    private String select() {
        return """
                SELECT r.id, r.product_id, p.product_code, r.variant_id, v.sku_code,
                       r.auxiliary_material_id, m.material_code, m.name AS material_name,
                       r.standard_quantity, r.unit, r.loss_rate, r.remark, r.enabled, r.created_at, r.updated_at
                FROM product_auxiliary_material r
                JOIN product p ON p.id=r.product_id
                JOIN product_variant v ON v.id=r.variant_id
                JOIN auxiliary_material m ON m.id=r.auxiliary_material_id
                """;
    }

    private RelationView map(ResultSet rs, int rowNum) throws SQLException {
        return new RelationView(rs.getString("id"), rs.getString("product_id"), rs.getString("product_code"),
                rs.getString("variant_id"), rs.getString("sku_code"), rs.getString("auxiliary_material_id"),
                rs.getString("material_code"), rs.getString("material_name"), rs.getBigDecimal("standard_quantity"),
                rs.getString("unit"), rs.getBigDecimal("loss_rate"), rs.getString("remark"), rs.getBoolean("enabled"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
}
