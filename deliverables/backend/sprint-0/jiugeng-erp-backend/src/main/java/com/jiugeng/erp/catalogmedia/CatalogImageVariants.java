package com.jiugeng.erp.catalogmedia;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

/** Durable, bounded historical thumbnail backfill. Originals and legacy thumbnails remain intact. */
@Component
class CatalogImageVariants {
    @org.springframework.beans.factory.annotation.Value("${app.catalog.image-maintenance-enabled:true}") private boolean enabled;
    private final java.util.concurrent.atomic.AtomicLong remaining=new java.util.concurrent.atomic.AtomicLong();
    private final JdbcClient jdbc;
    private final CatalogImageStore store;
    private final CatalogImageRepository assets;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    CatalogImageVariants(JdbcClient jdbc,CatalogImageStore store,CatalogImageRepository assets,io.micrometer.core.instrument.MeterRegistry metrics){this.jdbc=jdbc;this.store=store;this.assets=assets;this.metrics=metrics;metrics.gauge("catalog.image.backfill.remaining",remaining);}
    CatalogImageStore.Variant find(String id){return jdbc.sql("SELECT storage_key,media_type,etag FROM catalog_image_variant WHERE asset_id=:id AND storage_key IS NOT NULL").param("id",id)
        .query((r,n)->new CatalogImageStore.Variant(r.getString(1),r.getString(2),r.getString(3))).optional().orElse(null);}
    @Scheduled(fixedDelayString="${app.catalog.image-backfill-ms:30000}",initialDelayString="${app.catalog.image-backfill-initial-ms:45000}")
    void backfill(){
        if(!enabled)return;
        List<String> ids=jdbc.sql("""
            SELECT a.id FROM catalog_image_asset a LEFT JOIN catalog_image_variant v ON v.asset_id=a.id
            WHERE v.asset_id IS NULL OR (v.storage_key IS NULL AND v.next_attempt_at<=:now)
            ORDER BY a.created_at,a.id LIMIT 10
            """).param("now",Instant.now()).query(String.class).list();
        for(String id:ids)generate(id);
        remaining.set(jdbc.sql("SELECT COUNT(*) FROM catalog_image_asset a LEFT JOIN catalog_image_variant v ON v.asset_id=a.id WHERE v.storage_key IS NULL").query(Long.class).single());
    }
    void generate(String id){
        try{jdbc.sql("INSERT INTO catalog_image_variant(asset_id,next_attempt_at) VALUES(:id,:now)").param("id",id).param("now",Instant.now()).update();}
        catch(org.springframework.dao.DuplicateKeyException ignored){}
        if(jdbc.sql("UPDATE catalog_image_variant SET next_attempt_at=:lease WHERE asset_id=:id AND storage_key IS NULL AND next_attempt_at<=:now")
            .param("id",id).param("now",Instant.now()).param("lease",Instant.now().plus(Duration.ofMinutes(10))).update()!=1)return;
        CatalogImageStore.Variant variant=null;
        try{
            var asset=assets.find(id).orElseThrow();
            boolean lossless=switch(asset.purpose()){case SUPPLIER_QR,CUSTOMER_WECHAT,PURCHASE_WAYBILL->true;default->false;};
            variant=store.thumbnail(asset.originalKey(),lossless);
            int updated=jdbc.sql("UPDATE catalog_image_variant SET storage_key=:key,media_type=:type,etag=:etag WHERE asset_id=:id AND storage_key IS NULL")
                .param("id",id).param("key",variant.key()).param("type",variant.mediaType()).param("etag",variant.etag()).update();
            if(updated==0)store.delete(variant.key());
            else if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()){
                String key=variant.key();org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
                    @Override public void afterCompletion(int status){if(status!=STATUS_COMMITTED)try{store.delete(key);}catch(java.io.IOException ignored){}}
                });
            }
            metrics.counter("catalog.image.backfill","result","success").increment();
        }catch(Exception error){
            if(variant!=null)try{store.delete(variant.key());}catch(java.io.IOException ignored){}
            jdbc.sql("UPDATE catalog_image_variant SET next_attempt_at=:next WHERE asset_id=:id AND storage_key IS NULL")
                .param("id",id).param("next",Instant.now().plus(Duration.ofHours(1))).update();
            metrics.counter("catalog.image.backfill","result","failure").increment();
        }
    }
    void delete(String id)throws java.io.IOException{var v=find(id);if(v!=null)store.delete(v.key());}
}
