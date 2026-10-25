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
            String specification,String itemDimensions,String packageDimensions,
            BigDecimal itemLengthCm,BigDecimal itemWidthCm,BigDecimal itemHeightCm,
            BigDecimal packageLengthCm,BigDecimal packageWidthCm,BigDecimal packageHeightCm,BigDecimal packageWeight,
            String packageWeightUnit,BigDecimal packageWeightGrams,VariantStatus status,String imageAssetId,String imageUrl,
            long version,List<AttributeView> attributes,List<SupplierAssignment> suppliers,
            List<MaterialAssignment> materials,CustomsProfile customs,boolean nameHighlighted){}
    record ProductView(String id,String productCode,String productName,String nameZh,String nameEn,String brand,
            String internalCategory,String productType,String description,ProductStatus status,long version,
            List<VariantView> variants,int skuCount,boolean supplierDetailsAvailable,Instant createdAt,Instant updatedAt,String imageAssetId){}
    record VariantInput(String id,String skuCode,String variantName,String itemDimensions,String packageDimensions,
            BigDecimal itemLengthCm,BigDecimal itemWidthCm,BigDecimal itemHeightCm,
            BigDecimal packageLengthCm,BigDecimal packageWidthCm,BigDecimal packageHeightCm,
            BigDecimal packageWeightGrams,VariantStatus status,String imageAssetId,String imageUrl,Long version,
            List<SupplierAssignment> suppliers,List<MaterialAssignment> materials){}
    record ProductInput(String productCode,String productName,String productType,String description,
            ProductStatus status,Long version,List<VariantInput> variants,String imageAssetId){}
    record SkuListItem(String productId,String productCode,String productName,ProductStatus productStatus,
            String productType,VariantView variant,boolean businessAvailable,Instant updatedAt){}
    record SkuPage(List<SkuListItem> items,long totalCount,int page,int size){}
    record BatchVariantPatch(String productId,String variantId,long productVersion,long variantVersion,
            VariantStatus status,boolean statusSelected,BigDecimal itemLengthCm,BigDecimal itemWidthCm,
            BigDecimal itemHeightCm,boolean itemDimensionsSelected,BigDecimal packageLengthCm,
            BigDecimal packageWidthCm,BigDecimal packageHeightCm,boolean packageDimensionsSelected,
            BigDecimal packageWeightGrams,boolean packageWeightSelected){}
}
