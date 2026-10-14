package com.jiugeng.erp.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class ProductTypes {
    private ProductTypes() {}

    enum ProductStatus { DRAFT, ACTIVE, DISABLED }
    enum VariantStatus { ACTIVE, DISABLED }

    record AttributeView(String attributeCode, String attributeValue, int displayOrder) {}

    record VariantView(String id, String skuCode, String variantName, String barcode, String material,
            String specification, String itemDimensions, String packageDimensions, BigDecimal packageWeight,
            String packageWeightUnit, VariantStatus status, List<AttributeView> attributes) {}

    record ProductView(String id, String productCode, String nameZh, String nameEn, String brand,
            String internalCategory, String productType, String description, ProductStatus status,
            List<VariantView> variants, int skuCount, Instant createdAt, Instant updatedAt) {}

    record AttributeInput(String attributeCode, String attributeValue, int displayOrder) {}

    record VariantInput(String id, String skuCode, String variantName, String barcode, String material,
            String specification, String itemDimensions, String packageDimensions, BigDecimal packageWeight,
            String packageWeightUnit, VariantStatus status, List<AttributeInput> attributes) {}

    record ProductInput(String productCode, String nameZh, String nameEn, String brand,
            String internalCategory, String productType, String description, ProductStatus status,
            List<VariantInput> variants) {}
}
