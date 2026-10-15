package com.jiugeng.erp.catalogmedia;

import static com.jiugeng.erp.catalogmedia.CatalogImageGateway.Purpose;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CatalogImageService implements CatalogImageGateway {
    private final CatalogImageRepository repository;private final CatalogImageStore store;private final AuditTrail audit;
    private final long orphanHours;
    CatalogImageService(CatalogImageRepository repository,CatalogImageStore store,AuditTrail audit,
            @Value("${app.catalog.image-orphan-hours:24}")long orphanHours){this.repository=repository;this.store=store;this.audit=audit;this.orphanHours=orphanHours;}
    @Transactional
    Receipt upload(Purpose purpose,String fileName,byte[] bytes,AuthenticatedActor actor)throws IOException{
        String name=text(fileName,255);var stored=store.store(bytes);String id=UUID.randomUUID().toString();
        var asset=new CatalogImageRepository.Asset(id,purpose,"PENDING",stored.originalKey(),stored.thumbnailKey(),name,
                stored.sourceMediaType(),stored.storedMediaType(),stored.sourceSize(),stored.sha256(),stored.width(),stored.height(),actor.userId(),null,null,Instant.now());
        try{repository.insert(asset);}catch(RuntimeException e){delete(stored.originalKey());delete(stored.thumbnailKey());throw e;}
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"CATALOG_IMAGE_UPLOAD","CATALOG_IMAGE",id,true,null,
                Map.of("id",id,"purpose",purpose,"byteSize",stored.sourceSize(),"sha256",stored.sha256()),"上传受控目录图片"));
        return new Receipt(id,purpose,name,stored.storedMediaType(),stored.sourceSize(),stored.width(),stored.height(),stored.sha256());}
    @Transactional(readOnly=true)
    Content read(String id,boolean thumbnail,AuthenticatedActor actor,boolean productsRead,boolean suppliersRead)throws IOException{
        var image=asset(id);if("PENDING".equals(image.status())&&!image.uploadedBy().equals(actor.userId()))throw missing();
        boolean allowed=image.purpose()==Purpose.SKU_MAIN?productsRead:suppliersRead;if(!allowed)throw missing();
        return new Content(store.read(thumbnail?image.thumbnailKey():image.originalKey()),image.storedMediaType());}
    @Override @Transactional
    public void attach(String imageId,Purpose purpose,String type,String resourceId,AuthenticatedActor actor){if(imageId==null)return;try{var image=asset(imageId);
        if(image.purpose()!=purpose)throw new CatalogImageException(400,"CATALOG_IMAGE_PURPOSE_INVALID","图片用途与资源不匹配");
        if("ACTIVE".equals(image.status())){if(type.equals(image.resourceType())&&resourceId.equals(image.resourceId()))return;throw new CatalogImageException(409,"CATALOG_IMAGE_ALREADY_ATTACHED","图片已被其他资源引用");}
        if(repository.attach(imageId,purpose,actor.userId(),type,resourceId,Instant.now())!=1)throw new CatalogImageException(409,"CATALOG_IMAGE_ATTACH_CONFLICT","图片确认引用冲突");}catch(CatalogImageException e){throw new CatalogImageGateway.AttachmentException(e.status(),e.code(),e.getMessage());}}
    @Override @Transactional
    public void releaseReplaced(String oldId,String newId,String type,String resourceId){if(oldId!=null&&!oldId.equals(newId))repository.release(oldId,type,resourceId);}
    @Scheduled(cron="${app.catalog.image-cleanup-cron:0 45 4 * * *}") @Transactional
    public void cleanup(){Instant cutoff=Instant.now().minus(Duration.ofHours(orphanHours));for(var image:repository.orphans(cutoff)){try{store.delete(image.originalKey());store.delete(image.thumbnailKey());repository.deletePending(image.id());}catch(IOException ignored){}}}
    private CatalogImageRepository.Asset asset(String id){return repository.find(id).orElseThrow(this::missing);}
    private CatalogImageException missing(){return new CatalogImageException(404,"CATALOG_IMAGE_NOT_FOUND","图片不存在");}
    private String text(String value,int max){String result=value==null?null:value.trim();if(result==null||result.isEmpty()||result.length()>max)throw new CatalogImageException(400,"CATALOG_IMAGE_FILE_NAME_INVALID","文件名不正确");return result;}
    private void delete(String key){try{store.delete(key);}catch(IOException ignored){}}
    record Receipt(String id,Purpose purpose,String fileName,String mediaType,long byteSize,int width,int height,String sha256){}
    record Content(byte[] bytes,String mediaType){}
}
