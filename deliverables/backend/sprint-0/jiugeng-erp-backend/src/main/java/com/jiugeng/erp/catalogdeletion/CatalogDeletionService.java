package com.jiugeng.erp.catalogdeletion;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates catalog cleanup without deleting any business records or audit evidence. */
@Service
class CatalogDeletionService {
    private final JdbcClient jdbc;
    private final AuditTrail audit;
    CatalogDeletionService(JdbcClient jdbc,AuditTrail audit) {
        this.jdbc=jdbc;this.audit=audit;
    }
    record Entry(String id,long version,String image) {}
    private Entry lock(String table,String id) {
        return jdbc.sql("SELECT id,version"+(table.equals("customer_product")?"":",main_image_asset_id")+" FROM "+table+" WHERE id=:id AND deleted_at IS NULL FOR UPDATE")
            .param("id",id).query((r,n)->new Entry(r.getString("id"),r.getLong("version"),
                table.equals("customer_product")?null:r.getString("main_image_asset_id"))).optional()
            .orElseThrow(()->new Failure(404,"CATALOG_NOT_FOUND","记录已不存在，请刷新列表"));
    }
    private void version(Entry entry,long expected) {
        if(entry.version()!=expected) throw new Failure(409,"CATALOG_VERSION_CONFLICT","记录已被修改，请刷新后重新确认删除");
    }
    @Transactional
    void product(String id,String variantId,long productVersion,Long variantVersion,AuthenticatedActor actor) {
        Entry parent=lock("product",id);version(parent,productVersion);
        var variants=jdbc.sql("SELECT id,version,main_image_asset_id FROM product_variant WHERE product_id=:id AND deleted_at IS NULL ORDER BY id FOR UPDATE")
            .param("id",id).query((r,n)->new Entry(r.getString("id"),r.getLong("version"),r.getString("main_image_asset_id"))).list();
        var targets=variantId==null?variants:variants.stream().filter(v->v.id().equals(variantId)).toList();
        if(variantId!=null) {
            if(targets.isEmpty()) throw new Failure(404,"CATALOG_NOT_FOUND","SKU 不存在或不属于该父体");
            version(targets.getFirst(),Objects.requireNonNull(variantVersion));
        }

        for(var sku:targets) {
            var customers=jdbc.sql("SELECT id,version FROM customer_product WHERE variant_id=:id AND deleted_at IS NULL ORDER BY id FOR UPDATE")
                .param("id",sku.id()).query((r,n)->new Entry(r.getString("id"),r.getLong("version"),null)).list();
            for(var cp:customers) customerProduct(cp.id(),cp.version(),actor);
            jdbc.sql("UPDATE product_variant SET deleted_at=CURRENT_TIMESTAMP,deleted_by=:actor,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("actor",actor.userId()).param("id",sku.id()).update();
            record(actor,"PRODUCT_SKU_DELETE","PRODUCT_VARIANT",sku.id(),Map.of("productId",id,"version",sku.version(),"deletionMode","LOGICAL"));
        }
        if(variantId==null) {
            jdbc.sql("UPDATE product SET deleted_at=CURRENT_TIMESTAMP,deleted_by=:actor,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=:actor WHERE id=:id")
                .param("actor",actor.userId()).param("id",id).update();
        } else {
            jdbc.sql("UPDATE product SET version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=:actor WHERE id=:id")
                .param("id",id).param("actor",actor.userId()).update();
        }
        record(actor,variantId==null?"PRODUCT_DELETE":"PRODUCT_SKU_DELETE","PRODUCT",id,
            Map.of("version",parent.version(),"variantIds",targets.stream().map(Entry::id).toList(),"deletionMode","LOGICAL"));
    }
    @Transactional
    void customerProduct(String id,long version,AuthenticatedActor actor) {
        Entry cp=lock("customer_product",id);
        if(jdbc.sql("SELECT deleted_at FROM customer_product WHERE id=:id").param("id",id)
            .query((r,n)->r.getTimestamp("deleted_at")!=null).single())
            throw new Failure(404,"CATALOG_NOT_FOUND","客户商品已删除，请刷新列表");
        version(cp,version);
        jdbc.sql("UPDATE customer_product SET deleted_at=CURRENT_TIMESTAMP,deleted_by=:actor,deletion_key=id,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=:actor WHERE id=:id")
            .param("actor",actor.userId()).param("id",id).update();
        record(actor,"CUSTOMER_PRODUCT_DELETE","CUSTOMER_PRODUCT",id,Map.of("version",version,"deletionMode","LOGICAL"));
    }
    private void record(AuthenticatedActor actor,String action,String type,String id,Map<String,?> before) {
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,type,id,true,
            before,null,"目录逻辑删除；保留历史身份、资料及单据"));
    }
    static final class Failure extends RuntimeException {
        final int status; final String code;
        Failure(int status,String code,String message) { super(message);this.status=status;this.code=code; }
    }
}
