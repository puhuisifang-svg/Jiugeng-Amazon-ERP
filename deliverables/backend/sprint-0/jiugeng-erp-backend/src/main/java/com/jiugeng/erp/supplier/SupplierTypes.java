package com.jiugeng.erp.supplier;
import java.time.Instant;
final class SupplierTypes{private SupplierTypes(){}enum Status{ACTIVE,DISABLED}
record Input(String name,String contactPerson,String phone,String wechatId,String sourceUrl,String qrImageUrl,String qrImageId,String remark,Status status,Long version){}
record View(String id,String name,String contactPerson,String phone,String wechatId,String sourceUrl,String qrImageUrl,String qrImageId,String remark,Status status,long version,Instant createdAt,Instant updatedAt){}
record LinkInput(String supplierId,String purchaseUrl,String remark,boolean primary){}
record LinkView(String id,String ownerId,String supplierId,String supplierName,Status supplierStatus,String purchaseUrl,String remark,boolean primary,Instant updatedAt){}
record Page<T>(java.util.List<T> items,long totalCount,int page,int size){}
}
