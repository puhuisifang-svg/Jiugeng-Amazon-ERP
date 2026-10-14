package com.jiugeng.erp.product;

import static com.jiugeng.erp.product.ProductTypes.AttributeView;
import static com.jiugeng.erp.product.ProductTypes.ProductStatus;
import static com.jiugeng.erp.product.ProductTypes.ProductView;
import static com.jiugeng.erp.product.ProductTypes.VariantStatus;
import static com.jiugeng.erp.product.ProductTypes.VariantView;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProductRepository {
    private final JdbcClient jdbc;

    ProductRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    List<ProductView> findAll() {
        return jdbc.sql("""
                SELECT id, product_code, name_zh, name_en, brand, internal_category, product_type,
                       description, status, created_at, updated_at
                FROM product ORDER BY product_code_normalized
                """).query(this::mapProduct).list();
    }

    Optional<ProductView> findById(String id) {
        return jdbc.sql("""
                SELECT id, product_code, name_zh, name_en, brand, internal_category, product_type,
                       description, status, created_at, updated_at
                FROM product WHERE id = :id
                """).param("id", id).query(this::mapProduct).optional();
    }

    boolean productCodeExists(String normalized, String excludingId) {
        String sql = "SELECT COUNT(*) FROM product WHERE product_code_normalized = :normalized"
                + (excludingId == null ? "" : " AND id <> :id");
        var query = jdbc.sql(sql).param("normalized", normalized);
        if (excludingId != null) query = query.param("id", excludingId);
        return query.query(Integer.class).single() > 0;
    }

    boolean skuCodeExists(String normalized, String excludingId) {
        String sql = "SELECT COUNT(*) FROM product_variant WHERE sku_code_normalized = :normalized"
                + (excludingId == null ? "" : " AND id <> :id");
        var query = jdbc.sql(sql).param("normalized", normalized);
        if (excludingId != null) query = query.param("id", excludingId);
        return query.query(Integer.class).single() > 0;
    }

    boolean barcodeExists(String normalized, String excludingId) {
        if (normalized == null) return false;
        String sql = "SELECT COUNT(*) FROM product_variant WHERE barcode_normalized = :normalized"
                + (excludingId == null ? "" : " AND id <> :id");
        var query = jdbc.sql(sql).param("normalized", normalized);
        if (excludingId != null) query = query.param("id", excludingId);
        return query.query(Integer.class).single() > 0;
    }

    boolean variantBelongsToProduct(String variantId, String productId) {
        return jdbc.sql("SELECT COUNT(*) FROM product_variant WHERE id = :id AND product_id = :productId")
                .param("id", variantId).param("productId", productId).query(Integer.class).single() > 0;
    }

    List<String> findVariantIds(String productId) {
        return jdbc.sql("SELECT id FROM product_variant WHERE product_id = :productId")
                .param("productId", productId).query(String.class).list();
    }

    void insertProduct(String id, String code, String normalizedCode, String nameZh, String nameEn, String brand,
            String category, String type, String description, ProductStatus status, String actorId) {
        jdbc.sql("""
                INSERT INTO product (id, product_code, product_code_normalized, name_zh, name_en, brand,
                    internal_category, product_type, description, status, created_by, updated_by)
                VALUES (:id, :code, :normalizedCode, :nameZh, :nameEn, :brand, :category, :type,
                    :description, :status, :actorId, :actorId)
                """).param("id", id).param("code", code).param("normalizedCode", normalizedCode)
                .param("nameZh", nameZh).param("nameEn", nameEn).param("brand", brand)
                .param("category", category).param("type", type).param("description", description)
                .param("status", status.name()).param("actorId", actorId).update();
    }

    void updateProduct(String id, String code, String normalizedCode, String nameZh, String nameEn, String brand,
            String category, String type, String description, ProductStatus status, String actorId) {
        jdbc.sql("""
                UPDATE product SET product_code = :code, product_code_normalized = :normalizedCode,
                    name_zh = :nameZh, name_en = :nameEn, brand = :brand, internal_category = :category,
                    product_type = :type, description = :description, status = :status, updated_by = :actorId,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("id", id).param("code", code).param("normalizedCode", normalizedCode)
                .param("nameZh", nameZh).param("nameEn", nameEn).param("brand", brand)
                .param("category", category).param("type", type).param("description", description)
                .param("status", status.name()).param("actorId", actorId).update();
    }

    void insertVariant(String id, String productId, String skuCode, String normalizedSku, String variantName,
            String barcode, String normalizedBarcode, String material, String specification, String itemDimensions,
            String packageDimensions, BigDecimal packageWeight, VariantStatus status, String signature) {
        jdbc.sql("""
                INSERT INTO product_variant (id, product_id, sku_code, sku_code_normalized, variant_name,
                    barcode, barcode_normalized, material, specification, item_dimensions, package_dimensions,
                    package_weight, status, attribute_signature)
                VALUES (:id, :productId, :skuCode, :normalizedSku, :variantName, :barcode, :normalizedBarcode,
                    :material, :specification, :itemDimensions, :packageDimensions, :packageWeight, :status, :signature)
                """).param("id", id).param("productId", productId).param("skuCode", skuCode)
                .param("normalizedSku", normalizedSku).param("variantName", variantName).param("barcode", barcode)
                .param("normalizedBarcode", normalizedBarcode).param("material", material)
                .param("specification", specification).param("itemDimensions", itemDimensions)
                .param("packageDimensions", packageDimensions).param("packageWeight", packageWeight)
                .param("status", status.name()).param("signature", signature).update();
    }

    void updateVariant(String id, String skuCode, String normalizedSku, String variantName, String barcode,
            String normalizedBarcode, String material, String specification, String itemDimensions,
            String packageDimensions, BigDecimal packageWeight, VariantStatus status, String signature) {
        jdbc.sql("""
                UPDATE product_variant SET sku_code = :skuCode, sku_code_normalized = :normalizedSku,
                    variant_name = :variantName, barcode = :barcode, barcode_normalized = :normalizedBarcode,
                    material = :material, specification = :specification, item_dimensions = :itemDimensions,
                    package_dimensions = :packageDimensions, package_weight = :packageWeight, status = :status,
                    attribute_signature = :signature, updated_at = CURRENT_TIMESTAMP WHERE id = :id
                """).param("id", id).param("skuCode", skuCode).param("normalizedSku", normalizedSku)
                .param("variantName", variantName).param("barcode", barcode).param("normalizedBarcode", normalizedBarcode)
                .param("material", material).param("specification", specification).param("itemDimensions", itemDimensions)
                .param("packageDimensions", packageDimensions).param("packageWeight", packageWeight)
                .param("status", status.name()).param("signature", signature).update();
    }

    void reserveVariantSignatures(String productId, List<String> variantIds) {
        variantIds.forEach(id -> jdbc.sql("""
                UPDATE product_variant SET attribute_signature = :signature
                WHERE id = :id AND product_id = :productId
                """).param("signature", "__UPDATING__" + id).param("id", id)
                .param("productId", productId).update());
    }

    void disableVariant(String id) {
        jdbc.sql("UPDATE product_variant SET status = 'DISABLED', updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", id).update();
    }

    void replaceAttributes(String variantId, List<AttributeView> attributes) {
        jdbc.sql("DELETE FROM variant_attribute WHERE variant_id = :variantId").param("variantId", variantId).update();
        attributes.forEach(attribute -> jdbc.sql("""
                INSERT INTO variant_attribute
                    (variant_id, attribute_code, attribute_code_normalized, attribute_value, display_order)
                VALUES (:variantId, :code, :normalizedCode, :value, :displayOrder)
                """).param("variantId", variantId).param("code", attribute.attributeCode())
                .param("normalizedCode", normalize(attribute.attributeCode())).param("value", attribute.attributeValue())
                .param("displayOrder", attribute.displayOrder()).update());
    }

    void disableProduct(String id, String actorId) {
        jdbc.sql("UPDATE product SET status = 'DISABLED', updated_by = :actorId, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("actorId", actorId).param("id", id).update();
        jdbc.sql("UPDATE product_variant SET status = 'DISABLED', updated_at = CURRENT_TIMESTAMP WHERE product_id = :id")
                .param("id", id).update();
    }

    private ProductView mapProduct(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("id");
        List<VariantView> variants = jdbc.sql("""
                SELECT id, sku_code, variant_name, barcode, material, specification, item_dimensions,
                       package_dimensions, package_weight, status
                FROM product_variant WHERE product_id = :productId ORDER BY sku_code_normalized
                """).param("productId", id).query(this::mapVariant).list();
        return new ProductView(id, rs.getString("product_code"), rs.getString("name_zh"), rs.getString("name_en"),
                rs.getString("brand"), rs.getString("internal_category"), rs.getString("product_type"),
                rs.getString("description"), ProductStatus.valueOf(rs.getString("status")), variants, variants.size(),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private VariantView mapVariant(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("id");
        List<AttributeView> attributes = jdbc.sql("""
                SELECT attribute_code, attribute_value, display_order FROM variant_attribute
                WHERE variant_id = :variantId ORDER BY display_order, attribute_code_normalized
                """).param("variantId", id).query((row, number) -> new AttributeView(row.getString("attribute_code"),
                        row.getString("attribute_value"), row.getInt("display_order"))).list();
        return new VariantView(id, rs.getString("sku_code"), rs.getString("variant_name"), rs.getString("barcode"),
                rs.getString("material"), rs.getString("specification"), rs.getString("item_dimensions"),
                rs.getString("package_dimensions"), rs.getBigDecimal("package_weight"),
                VariantStatus.valueOf(rs.getString("status")), attributes);
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    static String normalize(String value) { return value.trim().toUpperCase(java.util.Locale.ROOT); }
}
