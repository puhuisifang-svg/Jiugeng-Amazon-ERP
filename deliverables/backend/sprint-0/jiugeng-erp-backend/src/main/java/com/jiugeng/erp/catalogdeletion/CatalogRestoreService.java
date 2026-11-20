package com.jiugeng.erp.catalogdeletion;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogRestoreService {
    record Deleted(String kind,String id,String productId,String name,String parentName,long version,Instant deletedAt) {}
    record Page(List<Deleted> items,long total,int page,int size) {}
    private final JdbcClient jdbc;private final AuditTrail audit;
    CatalogRestoreService(JdbcClient jdbc,AuditTrail audit){this.jdbc=jdbc;this.audit=audit;}
    @Transactional(readOnly=true)
    Page deleted(int page,int size){
        if(page<0||size<1||size>100)throw new CatalogDeletionService.Failure(400,"PAGE_INVALID","分页参数无效");
        String source="(SELECT 'PRODUCT' kind,id,id product_id,product_name name,product_name parent_name,version,deleted_at FROM product WHERE deleted_at IS NOT NULL UNION ALL SELECT 'SKU' kind,v.id,v.product_id,v.variant_name name,p.product_name parent_name,v.version,v.deleted_at FROM product_variant v JOIN product p ON p.id=v.product_id WHERE v.deleted_at IS NOT NULL) d";
        long count=jdbc.sql("SELECT COUNT(*) FROM "+source).query(Long.class).single();
        int current=Math.min(page,(int)Math.max(0,(count-1)/size));
        var items=jdbc.sql("SELECT * FROM "+source+" ORDER BY deleted_at DESC,id LIMIT :size OFFSET :offset").param("size",size).param("offset",(long)current*size).query((r,n)->new Deleted(r.getString("kind"),r.getString("id"),r.getString("product_id"),r.getString("name"),r.getString("parent_name"),r.getLong("version"),r.getTimestamp("deleted_at").toInstant())).list();
        return new Page(items,count,current,size);
    }
    @Transactional void restore(String kind,String id,long version,AuthenticatedActor actor){
        if(!Set.of("PRODUCT","SKU").contains(kind))throw new CatalogDeletionService.Failure(400,"KIND_INVALID","恢复类型无效");
        String table=kind.equals("PRODUCT")?"product":"product_variant";
        String parent=kind.equals("PRODUCT")?id:jdbc.sql("SELECT product_id FROM product_variant WHERE id=:id").param("id",id).query(String.class).optional().orElseThrow(()->new CatalogDeletionService.Failure(404,"SKU_NOT_FOUND","子体不存在"));
        boolean parentDeleted=jdbc.sql("SELECT deleted_at FROM product WHERE id=:id FOR UPDATE").param("id",parent).query((r,n)->r.getTimestamp(1)!=null).optional().orElseThrow(()->new CatalogDeletionService.Failure(404,"PRODUCT_NOT_FOUND","父体不存在"));
        if(kind.equals("SKU")&&parentDeleted)throw new CatalogDeletionService.Failure(409,"RESTORE_PARENT_FIRST","请先恢复所属父体，再恢复子体");
        var row=jdbc.sql("SELECT version,deleted_at FROM "+table+" WHERE id=:id FOR UPDATE").param("id",id).query((r,n)->Map.of("version",r.getLong("version"),"deleted",r.getTimestamp("deleted_at")!=null)).single();
        if(!(Boolean)row.get("deleted"))throw new CatalogDeletionService.Failure(409,"ALREADY_RESTORED","记录已恢复，请刷新列表");
        if((Long)row.get("version")!=version)throw new CatalogDeletionService.Failure(409,"VERSION_CONFLICT","记录已变化，请刷新后重试");
        String code=kind.equals("PRODUCT")?"product_code_normalized":"sku_code_normalized";
        int conflicts=jdbc.sql("SELECT COUNT(*) FROM "+table+" a JOIN "+table+" b ON a."+code+"=b."+code+" WHERE a.id=:id AND b.id<>a.id AND b.deleted_at IS NULL").param("id",id).query(Integer.class).single();
        if(conflicts>0)throw new CatalogDeletionService.Failure(409,"RESTORE_CONFLICT","存在相同内部标识的有效记录，无法恢复");
        if(kind.equals("SKU")){
            int invalid=jdbc.sql("SELECT COUNT(*) FROM product_variant_supplier l LEFT JOIN supplier s ON s.id=l.supplier_id WHERE l.variant_id=:id AND s.id IS NULL").param("id",id).query(Integer.class).single();
            invalid+=jdbc.sql("SELECT COUNT(*) FROM product_auxiliary_material l LEFT JOIN auxiliary_material m ON m.id=l.auxiliary_material_id WHERE l.variant_id=:id AND m.id IS NULL").param("id",id).query(Integer.class).single();
            if(invalid>0)throw new CatalogDeletionService.Failure(409,"RESTORE_RELATION_CONFLICT","历史关联档案缺失，请先处理关联后恢复");
        }
        jdbc.sql("UPDATE "+table+" SET deleted_at=NULL,deleted_by=NULL,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("id",id).update();
        jdbc.sql("UPDATE product SET "+(kind.equals("SKU")?"version=version+1,":"")+"updated_at=CURRENT_TIMESTAMP,updated_by=:actor WHERE id=:id").param("id",parent).param("actor",actor.userId()).update();
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),kind.equals("SKU")?"PRODUCT_SKU_RESTORE":"PRODUCT_RESTORE",kind.equals("SKU")?"PRODUCT_VARIANT":"PRODUCT",id,true,Map.of("deleted",true),Map.of("deleted",false),"恢复目录资料；客户商品不自动恢复，需重新关联"));
    }
}
