package com.jiugeng.erp.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class ProductTypes {
    private ProductTypes() {}
    enum ProductStatus { DRAFT, ACTIVE, DISABLED }
    enum VariantStatus { DRAFT, ACTIVE, DISABLED }
    record AttributeView(String attributeCode,String attributeValue,int displayOrder){}
    record SupplierAssignment(String supplierId,String purchaseUrl,String remark,boolean primary){}
    record MaterialAssignment(String materialId,BigDecimal standardQuantity,String unit,BigDecimal lossRate,
                              String remark,boolean enabled){}
    record VariantView(String id,String skuCode,String variantName,String barcode,String material,
            String specification,String itemDimensions,String packageDimensions,BigDecimal packageWeight,
            String packageWeightUnit,BigDecimal packageWeightGrams,VariantStatus status,String imageAssetId,
            long version,List<AttributeView> attributes,List<SupplierAssignment> suppliers,
            List<MaterialAssignment> materials){}
    record ProductView(String id,String productCode,String productName,String nameZh,String nameEn,String brand,
            String internalCategory,String productType,String description,ProductStatus status,long version,
            List<VariantView> variants,int skuCount,boolean supplierDetailsAvailable,Instant createdAt,Instant updatedAt){}
    record VariantInput(String id,String skuCode,String variantName,String itemDimensions,String packageDimensions,
            BigDecimal packageWeightGrams,VariantStatus status,String imageAssetId,Long version,
            List<SupplierAssignment> suppliers,List<MaterialAssignment> materials){}
    record ProductInput(String productCode,String productName,String productType,String description,
            ProductStatus status,Long version,List<VariantInput> variants){}
}
