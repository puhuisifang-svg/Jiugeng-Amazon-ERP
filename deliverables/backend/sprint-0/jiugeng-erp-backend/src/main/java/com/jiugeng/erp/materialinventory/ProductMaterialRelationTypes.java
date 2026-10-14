package com.jiugeng.erp.materialinventory;

import java.math.BigDecimal;
import java.time.Instant;

final class ProductMaterialRelationTypes {
    private ProductMaterialRelationTypes() {}

    record RelationInput(String productId, String variantId, String materialId, BigDecimal standardQuantity,
            String unit, BigDecimal lossRate, String remark, Boolean enabled) {}

    record ApplyAllInput(String productId, String materialId, BigDecimal standardQuantity, String unit,
            BigDecimal lossRate, String remark) {}

    record RelationView(String id, String productId, String productCode, String variantId, String skuCode,
            String materialId, String materialCode, String materialName, BigDecimal standardQuantity, String unit,
            BigDecimal lossRate, String remark, boolean enabled, Instant createdAt, Instant updatedAt) {}
}
