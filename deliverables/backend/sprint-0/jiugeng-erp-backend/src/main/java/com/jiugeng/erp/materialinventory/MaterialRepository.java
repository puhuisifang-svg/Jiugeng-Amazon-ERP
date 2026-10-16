package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.MaterialTypes.MaterialStatus;
import static com.jiugeng.erp.materialinventory.MaterialTypes.MaterialView;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class MaterialRepository {
    private final JdbcClient jdbc;

    MaterialRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    List<MaterialView> findAll() {
        return jdbc.sql("""
                SELECT id, material_code, name, material_type, specification, unit, image_url, image_asset_id, remark,
                       status, created_at, updated_at
                FROM auxiliary_material ORDER BY material_code_normalized
                """).query(this::map).list();
    }

    Optional<MaterialView> findById(String id) {
        return jdbc.sql("""
                SELECT id, material_code, name, material_type, specification, unit, image_url, image_asset_id, remark,
                       status, created_at, updated_at
                FROM auxiliary_material WHERE id = :id
                """).param("id", id).query(this::map).optional();
    }

    boolean codeExists(String normalized, String excludingId) {
        String sql = "SELECT COUNT(*) FROM auxiliary_material WHERE material_code_normalized = :normalized"
                + (excludingId == null ? "" : " AND id <> :id");
        var query = jdbc.sql(sql).param("normalized", normalized);
        if (excludingId != null) query = query.param("id", excludingId);
        return query.query(Integer.class).single() > 0;
    }

    void insert(String id, String code, String normalizedCode, String name, String type, String specification,
            String unit, String imageUrl, String imageAssetId, String remark, String actorId) {
        jdbc.sql("""
                INSERT INTO auxiliary_material (id, material_code, material_code_normalized, name, material_type,
                    specification, unit, image_url, image_asset_id, remark, status, created_by, updated_by)
                VALUES (:id, :code, :normalizedCode, :name, :type, :specification, :unit, :imageUrl, :imageAssetId, :remark,
                    'ACTIVE', :actorId, :actorId)
                """).param("id", id).param("code", code).param("normalizedCode", normalizedCode)
                .param("name", name).param("type", type).param("specification", specification).param("unit", unit)
                .param("imageUrl", imageUrl).param("imageAssetId", imageAssetId).param("remark", remark)
                .param("actorId", actorId).update();
    }

    void update(String id, String name, String type, String specification, String unit, String imageUrl,
            String imageAssetId, String remark, String actorId) {
        jdbc.sql("""
                UPDATE auxiliary_material SET name = :name, material_type = :type, specification = :specification,
                    unit = :unit, image_url = :imageUrl, image_asset_id = :imageAssetId, remark = :remark,
                    updated_by = :actorId,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("id", id).param("name", name).param("type", type).param("specification", specification)
                .param("unit", unit).param("imageUrl", imageUrl).param("imageAssetId", imageAssetId)
                .param("remark", remark)
                .param("actorId", actorId).update();
    }

    void disable(String id, String actorId) {
        jdbc.sql("""
                UPDATE auxiliary_material SET status = 'DISABLED', updated_by = :actorId,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("id", id).param("actorId", actorId).update();
    }

    void enable(String id, String actorId) {
        jdbc.sql("""
                UPDATE auxiliary_material SET status = 'ACTIVE', updated_by = :actorId,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("id", id).param("actorId", actorId).update();
    }

    boolean isReferenced(String id) {
        return jdbc.sql("""
                SELECT (SELECT COUNT(*) FROM inventory_transaction WHERE auxiliary_material_id=:id)
                     + (SELECT COUNT(*) FROM inventory_balance WHERE auxiliary_material_id=:id AND quantity<>0)
                     + (SELECT COUNT(*) FROM product_auxiliary_material WHERE auxiliary_material_id=:id)
                     + (SELECT COUNT(*) FROM purchase_order_line l JOIN purchase_order p ON p.id=l.order_id
                        WHERE p.order_type='MATERIAL' AND l.item_id=:id)
                """).param("id", id).query(Long.class).single() > 0;
    }

    void delete(String id) {
        jdbc.sql("DELETE FROM auxiliary_material_supplier WHERE auxiliary_material_id=:id").param("id", id).update();
        jdbc.sql("DELETE FROM inventory_balance WHERE auxiliary_material_id=:id AND quantity=0").param("id", id).update();
        jdbc.sql("DELETE FROM auxiliary_material WHERE id=:id").param("id", id).update();
    }

    private MaterialView map(ResultSet rs, int rowNum) throws SQLException {
        return new MaterialView(rs.getString("id"), rs.getString("material_code"), rs.getString("name"),
                rs.getString("material_type"), rs.getString("specification"), rs.getString("unit"),
                rs.getString("image_url"), rs.getString("image_asset_id"), rs.getString("remark"),
                MaterialStatus.valueOf(rs.getString("status")),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
    static String normalize(String value) { return value.trim().toUpperCase(Locale.ROOT); }
}
