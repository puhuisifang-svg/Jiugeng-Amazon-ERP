package com.jiugeng.erp.dashboard;

import static com.jiugeng.erp.dashboard.DashboardTypes.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuditHistoryLookup;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.product.ProductLookup;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DashboardService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final DashboardRepository repository;
    private final ProductLookup products;
    private final DashboardImageStore images;
    private final AuditTrail audit;
    private final AuditHistoryLookup auditHistory;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final long orphanHours;

    DashboardService(DashboardRepository repository, ProductLookup products, DashboardImageStore images,
            AuditTrail audit, AuditHistoryLookup auditHistory, ObjectMapper objectMapper, Clock clock,
            @Value("${app.dashboard.image-orphan-hours:24}") long orphanHours) {
        this.repository=repository;this.products=products;this.images=images;this.audit=audit;this.auditHistory=auditHistory;
        this.objectMapper=objectMapper;this.clock=clock;this.orphanHours=orphanHours;
    }

    @Transactional(readOnly=true)
    HomeSlice<TodayShipment> homeToday(){var date=businessDate();return HomeSlice.of(repository.homeToday(date),
            repository.countHomeToday(date),date,clock.instant());}
    @Transactional(readOnly=true)
    HomeSlice<PendingShipment> homePendingShipments(){var date=businessDate();return HomeSlice.of(
            repository.homePendingShipments(),repository.countHomePendingShipments(),date,clock.instant());}
    @Transactional(readOnly=true)
    HomeSlice<PendingProduct> homePendingProducts(){var date=businessDate();return HomeSlice.of(
            repository.homePendingProducts(),repository.countHomePendingProducts(),date,clock.instant());}

    @Transactional(readOnly=true)
    Page<TodayShipment> todayPage(String keyword,TodayStatus status,LocalDate from,LocalDate to,int page,int size){
        range(from,to);return repository.todayPage(text(keyword,128),status,from,to,page,size);}
    @Transactional(readOnly=true)
    Page<PendingShipment> pendingShipmentPage(String keyword,PendingShipmentStatus status,LocalDate from,LocalDate to,int page,int size){
        range(from,to);return repository.pendingShipmentPage(text(keyword,128),status,from,to,page,size);}
    @Transactional(readOnly=true)
    Page<PendingProduct> pendingProductPage(String keyword,PendingProductStatus status,LocalDate from,LocalDate to,int page,int size){
        range(from,to);return repository.pendingProductPage(text(keyword,128),status,from,to,page,size);}

    @Transactional
    TodayShipment createToday(ShipmentInput raw,String key,AuthenticatedActor actor){
        ShipmentInput input=shipment(raw);String hash=hash(input);var prior=command(actor,"TODAY_CREATE",key,hash);
        if(prior!=null)return today(prior.resourceId());
        if(input.date().isAfter(businessDate()))throw invalid("今日发货只能登记当天或历史安排");
        var product=product(input.productId());String id=UUID.randomUUID().toString();
        repository.insertToday(id,input,product.productCode(),product.name(),null,actor.userId());
        attach(input.imageId(),ImagePurpose.SHIPMENT,"TODAY_SHIPMENT",id,actor);
        TodayShipment created=today(id);record(actor,"DASHBOARD_TODAY_CREATE","TODAY_SHIPMENT",id,null,snapshot(created),"新增当日发货安排");
        saveCommand(actor,"TODAY_CREATE",key,hash,"TODAY_SHIPMENT",id);return created;
    }

    @Transactional
    TodayShipment updateToday(String id,ShipmentInput raw,long version,String correctionReason,AuthenticatedActor actor){
        TodayShipment before=today(id);if(before.status()!=TodayStatus.ACTIVE)throw state();
        if(before.shipDate().isBefore(businessDate()))required(correctionReason,"历史更正原因",500);
        ShipmentInput input=shipment(raw);var product=product(input.productId());
        if(input.date().isAfter(businessDate()))throw invalid("今日发货只能登记当天或历史安排");
        if(repository.updateToday(id,input,product.productCode(),product.name(),version,actor.userId())!=1)throw conflict();
        attach(input.imageId(),ImagePurpose.SHIPMENT,"TODAY_SHIPMENT",id,actor);
        releaseReplaced(before.imageId(),input.imageId(),"TODAY_SHIPMENT",id);
        TodayShipment updated=today(id);record(actor,"DASHBOARD_TODAY_UPDATE","TODAY_SHIPMENT",id,snapshot(before),
                snapshot(updated),before.shipDate().isBefore(businessDate())?required(correctionReason,"历史更正原因",500):"更新当日发货安排");return updated;
    }

    @Transactional
    TodayShipment archiveToday(String id,long version,String reason,String key,AuthenticatedActor actor){
        String cleaned=required(reason,"归档原因",500);String hash=hash(new StateCommand(id,version,cleaned));
        var prior=command(actor,"TODAY_ARCHIVE",key,hash);if(prior!=null)return today(prior.resourceId());
        TodayShipment before=today(id);if(repository.archiveToday(id,version,cleaned,actor.userId(),clock.instant())!=1)throw conflict();
        TodayShipment updated=today(id);record(actor,"DASHBOARD_TODAY_ARCHIVE","TODAY_SHIPMENT",id,snapshot(before),snapshot(updated),cleaned);
        saveCommand(actor,"TODAY_ARCHIVE",key,hash,"TODAY_SHIPMENT",id);return updated;
    }

    @Transactional
    PendingShipment createPendingShipment(ShipmentInput raw,String key,AuthenticatedActor actor){
        ShipmentInput input=shipment(raw);String hash=hash(input);var prior=command(actor,"PENDING_SHIPMENT_CREATE",key,hash);
        if(prior!=null)return pendingShipment(prior.resourceId());
        var product=product(input.productId());String id=UUID.randomUUID().toString();
        repository.insertPendingShipment(id,input,product.productCode(),product.name(),actor.userId());
        attach(input.imageId(),ImagePurpose.SHIPMENT,"PENDING_SHIPMENT",id,actor);
        var created=pendingShipment(id);record(actor,"DASHBOARD_PENDING_SHIPMENT_CREATE","PENDING_SHIPMENT",id,null,snapshot(created),"新增待发货安排");
        saveCommand(actor,"PENDING_SHIPMENT_CREATE",key,hash,"PENDING_SHIPMENT",id);return created;
    }

    @Transactional
    PendingShipment updatePendingShipment(String id,ShipmentInput raw,long version,AuthenticatedActor actor){
        PendingShipment before=pendingShipment(id);if(before.status()!=PendingShipmentStatus.PENDING)throw state();
        ShipmentInput input=shipment(raw);var product=product(input.productId());
        if(repository.updatePendingShipment(id,input,product.productCode(),product.name(),version,actor.userId())!=1)throw conflict();
        attach(input.imageId(),ImagePurpose.SHIPMENT,"PENDING_SHIPMENT",id,actor);
        releaseReplaced(before.imageId(),input.imageId(),"PENDING_SHIPMENT",id);
        var updated=pendingShipment(id);record(actor,"DASHBOARD_PENDING_SHIPMENT_UPDATE","PENDING_SHIPMENT",id,snapshot(before),snapshot(updated),"更新待发货安排");return updated;
    }

    @Transactional
    PendingShipment archivePendingShipment(String id,long version,String reason,String key,AuthenticatedActor actor){
        String cleaned=required(reason,"归档原因",500);String hash=hash(new StateCommand(id,version,cleaned));
        var prior=command(actor,"PENDING_SHIPMENT_ARCHIVE",key,hash);if(prior!=null)return pendingShipment(prior.resourceId());
        var before=pendingShipment(id);if(repository.archivePendingShipment(id,version,cleaned,actor.userId(),clock.instant())!=1)throw conflict();
        var updated=pendingShipment(id);record(actor,"DASHBOARD_PENDING_SHIPMENT_ARCHIVE","PENDING_SHIPMENT",id,snapshot(before),snapshot(updated),cleaned);
        saveCommand(actor,"PENDING_SHIPMENT_ARCHIVE",key,hash,"PENDING_SHIPMENT",id);return updated;
    }

    @Transactional
    TodayShipment transfer(String id,int quantity,int boxes,String remark,Instant latest,String imageId,long version,
                           String key,AuthenticatedActor actor){
        ShipmentInput raw=new ShipmentInput(businessDate(),"source",imageId,quantity,boxes,remark,latest);
        String hash=hash(new TransferCommand(id,quantity,boxes,remark==null?"":remark,latest.toString(),
                imageId==null?"":imageId,version));
        var prior=command(actor,"PENDING_SHIPMENT_TRANSFER",key,hash);if(prior!=null)return today(prior.resourceId());
        var source=repository.pendingShipmentForUpdate(id).orElseThrow(()->missing("PENDING_SHIPMENT_NOT_FOUND","待发货记录不存在"));
        if(source.status()!=PendingShipmentStatus.PENDING)throw state();
        ShipmentInput input=shipment(new ShipmentInput(raw.date(),source.productId(),raw.imageId()==null?source.imageId():raw.imageId(),
                raw.quantity(),raw.boxCount(),raw.remark(),raw.latestShipAt()));
        String todayId=UUID.randomUUID().toString();repository.insertToday(todayId,input,source.productCode(),source.productName(),id,actor.userId());
        if(repository.markTransferred(id,version,actor.userId(),clock.instant())!=1)throw conflict();
        attachTransferred(input.imageId(),todayId,id,actor);
        var today=today(todayId);record(actor,"DASHBOARD_PENDING_SHIPMENT_TRANSFER","PENDING_SHIPMENT",id,snapshot(source),snapshot(today),"转入当日发货安排");
        saveCommand(actor,"PENDING_SHIPMENT_TRANSFER",key,hash,"TODAY_SHIPMENT",todayId);return today;
    }

    @Transactional
    PendingProduct createPendingProduct(PendingProductInput raw,String key,AuthenticatedActor actor){
        var input=pendingProductInput(raw);String hash=hash(input);var prior=command(actor,"PENDING_PRODUCT_CREATE",key,hash);
        if(prior!=null)return pendingProduct(prior.resourceId());String id=UUID.randomUUID().toString();
        repository.insertPendingProduct(id,input,actor.userId());attach(input.imageId(),ImagePurpose.PENDING_PRODUCT,"PENDING_PRODUCT",id,actor);
        var created=pendingProduct(id);record(actor,"DASHBOARD_PENDING_PRODUCT_CREATE","PENDING_PRODUCT",id,null,snapshot(created),"新增待开发新品");
        saveCommand(actor,"PENDING_PRODUCT_CREATE",key,hash,"PENDING_PRODUCT",id);return created;
    }

    @Transactional
    PendingProduct updatePendingProduct(String id,PendingProductInput raw,long version,AuthenticatedActor actor){
        var before=pendingProduct(id);if(before.status()!=PendingProductStatus.PENDING)throw state();var input=pendingProductInput(raw);
        if(repository.updatePendingProduct(id,input,version,actor.userId())!=1)throw conflict();
        attach(input.imageId(),ImagePurpose.PENDING_PRODUCT,"PENDING_PRODUCT",id,actor);
        releaseReplaced(before.imageId(),input.imageId(),"PENDING_PRODUCT",id);var updated=pendingProduct(id);
        record(actor,"DASHBOARD_PENDING_PRODUCT_UPDATE","PENDING_PRODUCT",id,snapshot(before),snapshot(updated),"更新待开发新品");return updated;
    }

    @Transactional
    PendingProduct completePendingProduct(String id,long version,String key,AuthenticatedActor actor){
        String hash=hash(new VersionCommand(id,version));var prior=command(actor,"PENDING_PRODUCT_COMPLETE",key,hash);
        if(prior!=null)return pendingProduct(prior.resourceId());var before=pendingProduct(id);
        if(repository.completePendingProduct(id,version,actor.userId(),clock.instant())!=1)throw conflict();var updated=pendingProduct(id);
        record(actor,"DASHBOARD_PENDING_PRODUCT_COMPLETE","PENDING_PRODUCT",id,snapshot(before),snapshot(updated),"标记新品开发完成");
        saveCommand(actor,"PENDING_PRODUCT_COMPLETE",key,hash,"PENDING_PRODUCT",id);return updated;
    }

    @Transactional
    PendingProduct archivePendingProduct(String id,long version,String reason,String key,AuthenticatedActor actor){
        String cleaned=required(reason,"归档原因",500);String hash=hash(new StateCommand(id,version,cleaned));
        var prior=command(actor,"PENDING_PRODUCT_ARCHIVE",key,hash);if(prior!=null)return pendingProduct(prior.resourceId());var before=pendingProduct(id);
        if(repository.archivePendingProduct(id,version,cleaned,actor.userId(),clock.instant())!=1)throw conflict();var updated=pendingProduct(id);
        record(actor,"DASHBOARD_PENDING_PRODUCT_ARCHIVE","PENDING_PRODUCT",id,snapshot(before),snapshot(updated),cleaned);
        saveCommand(actor,"PENDING_PRODUCT_ARCHIVE",key,hash,"PENDING_PRODUCT",id);return updated;
    }

    @Transactional
    ImageReceipt uploadImage(ImagePurpose purpose,String fileName,byte[] bytes,AuthenticatedActor actor) throws IOException {
        String safeName=required(fileName,"文件名",255);DashboardImageStore.StoredImage stored=images.store(bytes);String id=UUID.randomUUID().toString();
        var asset=new ImageAsset(id,purpose,ImageStatus.PENDING,stored.originalKey(),stored.thumbnailKey(),safeName,
                stored.sourceMediaType(),stored.storedMediaType(),stored.sourceSize(),stored.sha256(),stored.width(),stored.height(),
                actor.userId(),null,null,clock.instant(),null);
        try { repository.insertImage(asset); }
        catch(RuntimeException exception){bestEffortDelete(stored.originalKey());bestEffortDelete(stored.thumbnailKey());throw exception;}
        record(actor,"DASHBOARD_IMAGE_UPLOAD","DASHBOARD_IMAGE",id,null,Map.of("id",id,"purpose",purpose,
                "mediaType",stored.sourceMediaType(),"byteSize",stored.sourceSize(),"sha256",stored.sha256()),"上传产品图案");
        return new ImageReceipt(id,purpose,safeName,stored.storedMediaType(),stored.sourceSize(),stored.width(),stored.height(),stored.sha256());
    }

    @Transactional(readOnly=true)
    ImageContent image(String id,boolean thumbnail,AuthenticatedActor actor,boolean shipmentManager,
                       boolean productManager) throws IOException {
        var image=repository.image(id).orElseThrow(()->missing("DASHBOARD_IMAGE_NOT_FOUND","图片不存在"));
        if(image.status()==ImageStatus.PENDING&&!image.uploadedBy().equals(actor.userId()))throw missing("DASHBOARD_IMAGE_NOT_FOUND","图片不存在");
        if(image.status()==ImageStatus.ACTIVE&&!repository.imageVisible(image,businessDate(),shipmentManager,productManager))
            throw missing("DASHBOARD_IMAGE_NOT_FOUND","图片不存在");
        return new ImageContent(images.read(thumbnail?image.thumbnailStorageKey():image.originalStorageKey()),image.storedMediaType());
    }

    @Transactional(readOnly=true)
    java.util.List<AuditHistoryLookup.HistoryEvent> history(String type,String id){
        switch(type){case "TODAY_SHIPMENT"->today(id);case "PENDING_SHIPMENT"->pendingShipment(id);
            case "PENDING_PRODUCT"->pendingProduct(id);default->throw invalid("历史资源类型不正确");}
        return auditHistory.find(type,id);
    }

    @Scheduled(cron="${app.dashboard.image-cleanup-cron:0 15 4 * * *}")
    @Transactional
    public void cleanupOrphans(){Instant cutoff=clock.instant().minus(java.time.Duration.ofHours(orphanHours));
        for(var image:repository.orphanImages(cutoff)){try{images.delete(image.originalStorageKey());images.delete(image.thumbnailStorageKey());repository.deletePendingImage(image.id());}
            catch(IOException exception){/* retain metadata so a later run can retry safely */}}}

    record ImageContent(byte[] bytes,String mediaType){}

    private ShipmentInput shipment(ShipmentInput input){if(input==null||input.date()==null||input.latestShipAt()==null)throw invalid("发货日期和最晚发货时间必填");
        String productId=required(input.productId(),"产品",36);if(input.quantity()<=0||input.boxCount()<=0)throw invalid("数量和箱数必须大于 0");
        LocalDate latestDate=input.latestShipAt().atZone(BUSINESS_ZONE).toLocalDate();if(!latestDate.equals(input.date()))throw invalid("最晚发货时间必须落在发货日期当天");
        return new ShipmentInput(input.date(),productId,blank(input.imageId()),input.quantity(),input.boxCount(),text(input.remark(),500),input.latestShipAt());}
    private PendingProductInput pendingProductInput(PendingProductInput input){if(input==null||input.expectedCompletionDate()==null||input.expectedQuantity()<=0)throw invalid("预计数量必须大于 0且预计完成日期必填");
        return new PendingProductInput(required(input.productName(),"产品名称",128),blank(input.imageId()),input.expectedQuantity(),text(input.remark(),500),input.expectedCompletionDate());}
    private ProductLookup.ProductReference product(String id){var product=products.findById(id).orElseThrow(()->missing("PRODUCT_NOT_FOUND","产品不存在"));
        if(!product.active())throw new DashboardException(409,"PRODUCT_DISABLED","不允许引用停用产品");if(product.name().length()>128)throw invalid("产品名称超过工作台快照上限 128 字符");return product;}
    private void attach(String imageId,ImagePurpose purpose,String type,String resourceId,AuthenticatedActor actor){if(imageId==null)return;var image=repository.image(imageId).orElseThrow(()->missing("DASHBOARD_IMAGE_NOT_FOUND","图片不存在"));
        if(image.purpose()!=purpose)throw invalid("图片用途与业务记录不匹配");if(image.status()==ImageStatus.ACTIVE){if(!type.equals(image.attachedResourceType())||!resourceId.equals(image.attachedResourceId()))throw new DashboardException(409,"DASHBOARD_IMAGE_ALREADY_ATTACHED","图片已被其他记录使用");return;}
        if(repository.attachImage(imageId,purpose,actor.userId(),type,resourceId,clock.instant())!=1)throw new DashboardException(409,"DASHBOARD_IMAGE_ATTACH_CONFLICT","图片确认引用冲突");}
    private void attachTransferred(String imageId,String targetId,String sourceId,AuthenticatedActor actor){if(imageId==null)return;
        var image=repository.image(imageId).orElseThrow(()->missing("DASHBOARD_IMAGE_NOT_FOUND","图片不存在"));
        if(image.status()==ImageStatus.ACTIVE&&"PENDING_SHIPMENT".equals(image.attachedResourceType())
                &&sourceId.equals(image.attachedResourceId())){if(repository.transferImage(imageId,sourceId,targetId,clock.instant())!=1)throw conflict();return;}
        attach(imageId,ImagePurpose.SHIPMENT,"TODAY_SHIPMENT",targetId,actor);}
    private void releaseReplaced(String oldId,String newId,String type,String resourceId){
        if(oldId!=null&&!oldId.equals(newId))repository.releaseImage(oldId,type,resourceId,clock.instant());}
    private void bestEffortDelete(String key){try{images.delete(key);}catch(IOException ignored){}}
    private TodayShipment today(String id){return repository.today(id).orElseThrow(()->missing("TODAY_SHIPMENT_NOT_FOUND","当日发货安排不存在"));}
    private PendingShipment pendingShipment(String id){return repository.pendingShipment(id).orElseThrow(()->missing("PENDING_SHIPMENT_NOT_FOUND","待发货记录不存在"));}
    private PendingProduct pendingProduct(String id){return repository.pendingProduct(id).orElseThrow(()->missing("PENDING_PRODUCT_NOT_FOUND","待开发新品不存在"));}
    private Command command(AuthenticatedActor actor,String operation,String key,String payload){String cleanKey=key(key);var command=repository.command(actor.userId(),operation,cleanKey).orElse(null);if(command!=null&&!command.payloadHash().equals(payload))throw new DashboardException(409,"IDEMPOTENCY_KEY_CONFLICT","幂等键已用于不同请求");return command;}
    private void saveCommand(AuthenticatedActor actor,String operation,String key,String payload,String type,String id){repository.insertCommand(new Command(actor.userId(),operation,key(key),payload,type,id));}
    private String key(String key){if(key==null||!key.matches("[A-Za-z0-9._:-]{1,128}"))throw invalid("Idempotency-Key 格式不正确");return key;}
    private String hash(Object value){try{return sha(objectMapper.writeValueAsString(value));}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private String sha(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void record(AuthenticatedActor actor,String action,String type,String id,Map<String,?> before,Map<String,?> after,String detail){audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,type,id,true,before,after,detail));}
    private Map<String,Object> snapshot(Object value){@SuppressWarnings("unchecked") Map<String,Object> result=objectMapper.convertValue(value,LinkedHashMap.class);return result;}
    private void range(LocalDate from,LocalDate to){if(from!=null&&to!=null&&from.isAfter(to))throw invalid("开始日期不能晚于结束日期");}
    private LocalDate businessDate(){return LocalDate.now(clock.withZone(BUSINESS_ZONE));}
    private String required(String value,String label,int max){String result=blank(value);if(result==null||result.length()>max)throw invalid(label+"不能为空且长度不能超过 "+max);return result;}
    private String text(String value,int max){String result=blank(value);if(result!=null&&result.length()>max)throw invalid("字段长度不能超过 "+max);return result;}
    private String blank(String value){if(value==null)return null;String result=value.trim();return result.isEmpty()?null:result;}
    private DashboardException invalid(String message){return new DashboardException(400,"DASHBOARD_FIELD_INVALID",message);}
    private DashboardException missing(String code,String message){return new DashboardException(404,code,message);}
    private DashboardException conflict(){return new DashboardException(409,"DASHBOARD_VERSION_CONFLICT","记录已被更新或状态不允许当前操作");}
    private DashboardException state(){return new DashboardException(409,"DASHBOARD_STATE_INVALID","当前状态不允许此操作");}
    private record StateCommand(String id,long version,String reason){}
    private record VersionCommand(String id,long version){}
    private record TransferCommand(String source,int quantity,int boxes,String remark,String latest,String image,long version){}
}
