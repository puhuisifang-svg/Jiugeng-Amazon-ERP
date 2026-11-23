package com.jiugeng.erp.catalogmedia;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

/** Live source mirror, not a replacement for independent business snapshots. */
@Service
class ExternalImageService {
    @org.springframework.beans.factory.annotation.Value("${app.catalog.image-maintenance-enabled:true}") private boolean enabled;
    private final JdbcClient jdbc;private final CatalogImageStore store;private final ExternalImageFetcher fetcher;
    private final List<ExternalImageSource> sources;private final io.micrometer.core.instrument.MeterRegistry metrics;
    private final Map<ExternalImageSource,String> cursors=new HashMap<>();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(24),r->{Thread t=new Thread(r,"external-image-sync");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Set<String> queued=ConcurrentHashMap.newKeySet();
    private final org.springframework.transaction.support.TransactionTemplate transaction;
    ExternalImageService(JdbcClient jdbc,CatalogImageStore store,ExternalImageFetcher fetcher,List<ExternalImageSource> sources,io.micrometer.core.instrument.MeterRegistry metrics,org.springframework.transaction.PlatformTransactionManager tx){this.jdbc=jdbc;this.store=store;this.fetcher=fetcher;this.sources=sources;this.metrics=metrics;this.transaction=new org.springframework.transaction.support.TransactionTemplate(tx);}
    record Mirror(String id,String url,String owner,boolean lossless,String original,String thumbnail,String originalType,String thumbnailType,String sha,String etag,String modified,Instant checked,Instant next,int failures){}
    Mirror find(String id){return jdbc.sql("SELECT * FROM catalog_external_image WHERE id=:id").param("id",id).query((r,n)->new Mirror(r.getString("id"),r.getString("source_url"),r.getString("created_by"),r.getBoolean("lossless"),r.getString("original_key"),r.getString("thumbnail_key"),r.getString("original_media_type"),r.getString("thumbnail_media_type"),r.getString("content_sha"),r.getString("source_etag"),r.getString("source_modified"),r.getTimestamp("checked_at")==null?null:r.getTimestamp("checked_at").toInstant(),r.getTimestamp("next_check_at").toInstant(),r.getInt("failure_count"))).optional().orElseThrow(()->new CatalogImageException(404,"IMAGE_NOT_FOUND","图片不存在"));}
    private boolean has(Authentication a,String p){return a.getAuthorities().stream().anyMatch(v->v.getAuthority().equals(p));}
    private ExternalImageSource source(String url,Authentication a){return sources.stream().filter(s->has(a,s.permission())&&s.contains(url)).findFirst().orElse(null);}
    String resolve(String url,String draftPermission,Authentication a,AuthenticatedActor actor)throws IOException {
        ExternalImageFetcher.validate(url);var source=source(url,a);
        boolean draft=source==null&&sources.stream().anyMatch(s->s.permission().equals(draftPermission)&&has(a,draftPermission.replace(".read",".write")));
        if(source==null&&!draft)throw new CatalogImageException(403,"IMAGE_SOURCE_DENIED","当前资料未关联此图片，或没有查看权限");
        boolean lossless=source!=null?source.lossless():"suppliers.read".equals(draftPermission);
        String id=register(url,lossless,draft?actor.userId():null);
        if(draft)try{jdbc.sql("INSERT INTO catalog_external_image_grant(image_id,actor_id,permission) VALUES(:image,:actor,:permission)").param("image",id).param("actor",actor.userId()).param("permission",draftPermission.replace(".read",".write")).update();}catch(org.springframework.dao.DuplicateKeyException ignored){}
        enqueue(id);return id;
    }
    Mirror authorized(String id,Authentication a,AuthenticatedActor actor){
        var image=find(id);
        boolean owner=jdbc.sql("SELECT permission FROM catalog_external_image_grant WHERE image_id=:image AND actor_id=:actor").param("image",id).param("actor",actor.userId()).query(String.class).list().stream().anyMatch(p->has(a,p));
        if(!owner&&source(image.url(),a)==null)throw new CatalogImageException(403,"IMAGE_SOURCE_DENIED","当前账号无权查看此图片");
        jdbc.sql("UPDATE catalog_external_image SET last_accessed_at=:now WHERE id=:id").param("now",Instant.now()).param("id",id).update();
        if(!image.next().isAfter(Instant.now()))enqueue(id);
        return image;
    }
    String register(String url,boolean lossless,String owner){
        String id=ImageHttpResponse.hash((lossless?"lossless:":"photo:")+url);
        try{jdbc.sql("INSERT INTO catalog_external_image(id,source_url,lossless,created_by) VALUES(:id,:url,:lossless,:owner)").param("id",id).param("url",url).param("lossless",lossless).param("owner",owner).update();}
        catch(org.springframework.dao.DuplicateKeyException ignored){}
        return id;
    }
    void enqueue(String id){if(!queued.add(id))return;try{worker.execute(()->{try{refresh(id);}finally{queued.remove(id);}});}catch(RejectedExecutionException ignored){queued.remove(id);}}
    @jakarta.annotation.PreDestroy void close(){worker.shutdownNow();}
    @Scheduled(fixedDelayString="${app.catalog.external-scan-ms:30000}",initialDelayString="${app.catalog.external-initial-ms:60000}")
    void tick(){
        if(!enabled)return;
        // Each module owns its source enumeration; network work never runs on the scheduler or a DB transaction.
        for(var source:sources){
            var batch=source.scan(cursors.getOrDefault(source,""),10);var urls=batch.urls();
            for(String url:urls){try{ExternalImageFetcher.validate(url);enqueue(register(url,source.lossless(),null));}catch(IOException ignored){metrics.counter("catalog.image.external","result","invalid-source").increment();}}
            cursors.put(source,batch.cursor());
        }
        jdbc.sql("SELECT id FROM catalog_external_image WHERE next_check_at<=:now AND (lease_until IS NULL OR lease_until<:now) ORDER BY next_check_at LIMIT 24").param("now",Instant.now()).query(String.class).list().forEach(this::enqueue);
        for(String key:jdbc.sql("SELECT storage_key FROM catalog_image_retired_file WHERE delete_after<:now LIMIT 30").param("now",Instant.now()).query(String.class).list())try{store.delete(key);jdbc.sql("DELETE FROM catalog_image_retired_file WHERE storage_key=:key").param("key",key).update();}catch(IOException ignored){}
    }
    void refresh(String id){
        Instant now=Instant.now(),lease=now.plusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if(jdbc.sql("UPDATE catalog_external_image SET lease_until=:lease WHERE id=:id AND next_check_at<=:now AND (lease_until IS NULL OR lease_until<:now)").param("id",id).param("now",now).param("lease",lease).update()!=1)return;
        var old=find(id);CatalogImageStore.StoredImage stored=null;CatalogImageStore.Variant thumb=null;
        try{
            if(old.owner()==null&&sources.stream().noneMatch(s->s.contains(old.url()))){defer(id,lease,Duration.ofHours(24),false);return;}
            // At least daily fetch full bytes even if an origin incorrectly keeps its ETag/Last-Modified.
            boolean force=old.checked()==null||old.checked().isBefore(now.minus(Duration.ofHours(24)));
            var result=fetcher.fetch(old.url(),force?null:old.etag(),force?null:old.modified());
            if(result.unchanged()){defer(id,lease,Duration.ofHours(1),false);metrics.counter("catalog.image.external","result","unchanged").increment();return;}
            String sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(result.bytes()));
            if(sha.equals(old.sha())){
                jdbc.sql("UPDATE catalog_external_image SET source_etag=:etag,source_modified=:modified,checked_at=:now,next_check_at=:next,failure_count=0,lease_until=NULL WHERE id=:id AND lease_until=:lease")
                    .param("id",id).param("lease",lease).param("etag",result.etag()).param("modified",result.modified()).param("now",now).param("next",now.plus(Duration.ofHours(1))).update();
                metrics.counter("catalog.image.external","result","unchanged").increment();return;
            }
            stored=store.store(result.bytes(),!old.lossless());thumb=store.thumbnail(stored.originalKey(),old.lossless());
            var uploaded=stored;var small=thumb;
            transaction.executeWithoutResult(tx->{
                int changed=jdbc.sql("UPDATE catalog_external_image SET original_key=:original,thumbnail_key=:thumb,original_media_type=:type,thumbnail_media_type=:smallType,content_sha=:sha,source_etag=:etag,source_modified=:modified,checked_at=:now,next_check_at=:next,failure_count=0,lease_until=NULL WHERE id=:id AND lease_until=:lease")
                    .param("lease",lease).param("id",id).param("original",uploaded.originalKey()).param("thumb",small.key()).param("type",uploaded.storedMediaType()).param("smallType",small.mediaType()).param("sha",sha).param("etag",result.etag()).param("modified",result.modified()).param("now",now).param("next",now.plus(Duration.ofHours(1))).update();
                if(changed!=1)throw new java.util.ConcurrentModificationException("Image sync lease changed");
                retire(old.original());retire(old.thumbnail());retire(uploaded.thumbnailKey());
            });
            metrics.counter("catalog.image.external","result","updated").increment();
        }catch(Exception failure){
            // Do not replace the last good pointer on errors, timeouts, invalid images or origin deletion.
            if(thumb!=null)remove(thumb.key());if(stored!=null){remove(stored.originalKey());remove(stored.thumbnailKey());}
            defer(id,lease,Duration.ofMinutes(Math.min(360,5L<<Math.min(old.failures(),6))),true);
            metrics.counter("catalog.image.external","result","failure").increment();
        }
    }
    private void retire(String key){if(key!=null)jdbc.sql("INSERT INTO catalog_image_retired_file(storage_key,delete_after) VALUES(:key,:at)").param("key",key).param("at",Instant.now().plus(Duration.ofHours(24))).update();}
    private void remove(String key){try{store.delete(key);}catch(IOException ignored){}}
    private void defer(String id,Instant lease,Duration delay,boolean failed){jdbc.sql("UPDATE catalog_external_image SET next_check_at=:next,lease_until=NULL,failure_count="+(failed?"failure_count+1":"0")+" WHERE id=:id AND lease_until=:lease").param("id",id).param("lease",lease).param("next",Instant.now().plus(delay)).update();}
}
