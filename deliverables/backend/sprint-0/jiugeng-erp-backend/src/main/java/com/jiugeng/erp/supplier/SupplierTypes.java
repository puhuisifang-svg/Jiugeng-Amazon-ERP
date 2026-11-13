package com.jiugeng.erp.supplier;
import java.time.Instant;
final class SupplierTypes{private SupplierTypes(){}enum Status{ACTIVE,DISABLED} enum Category{MATERIAL,PRODUCT}
record Input(String name,String contactPerson,String phone,String wechatId,String sourceUrl,String qrImageUrl,String qrImageId,String remark,Status status,Long version,java.util.List<Category> categories){}
record View(String id,String name,String contactPerson,String phone,String wechatId,String sourceUrl,String qrImageUrl,String qrImageId,String remark,Status status,long version,Instant createdAt,Instant updatedAt,java.util.List<Category> categories){}
record LinkInput(String supplierId,String purchaseUrl,String remark,boolean primary){}
record LinkView(String id,String ownerId,String supplierId,String supplierName,Status supplierStatus,String purchaseUrl,String remark,boolean primary,Instant updatedAt){}
record Dependency(String type,String id,String parentId,String code,String name,boolean accessible,boolean archived){}
record Page<T>(java.util.List<T> items,long totalCount,int page,int size){}
}
