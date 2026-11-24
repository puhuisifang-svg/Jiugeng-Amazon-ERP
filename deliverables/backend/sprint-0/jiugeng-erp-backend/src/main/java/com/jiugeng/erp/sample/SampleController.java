package com.jiugeng.erp.sample;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuditHistoryLookup;
import com.jiugeng.erp.product.ProductLookup;
import com.jiugeng.erp.catalogmedia.CatalogImageGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController @Profile("!mock") @RequestMapping("/api/v1/samples") @Transactional
class SampleController {
    private final JdbcClient jdbc;
    private final ProductLookup products;
    private final AuditTrail audit;
    private final AuditHistoryLookup history;
    private final CatalogImageGateway images;
    private final ObjectMapper json;
    SampleController(JdbcClient jdbc,ProductLookup products,AuditTrail audit,CatalogImageGateway images,AuditHistoryLookup history,ObjectMapper json){this.jdbc=jdbc;this.products=products;this.audit=audit;this.images=images;this.history=history;this.json=json;}
    record Input(String productId,String variantId,String locationCode,Long version,String sampleName,String sampleCode,String specification,String remark,String storageImageId,String sampleImageId,String copyFromId){}
    record Row(String id,String productId,String variantId,String productName,String skuCode,String skuName,String locationCode,long version,Instant updatedAt,String specification,String remark,String storageImageId,String sampleImageId,Instant deletedAt,String snapshotImageUrl,boolean imageSnapshotTaken,String currentProductName,String currentSkuName,String linkedStatus,String displayImageId,String displayImageUrl,String imageSource){}
    record Page(List<Row> items,long totalCount,int page,int size){}
    record Version(long version){}
    record Selection(List<String> ids){}
    record Query(String q,String productId,String source,String photo,String location,String locationMode,boolean deleted){}
    record Where(String sql,Map<String,Object> params){}
    private final org.springframework.jdbc.core.RowMapper<Row> mapper=(r,n)->new Row(r.getString("id"),r.getString("product_id"),r.getString("variant_id"),r.getString("product_name"),r.getString("sku_code"),r.getString("sku_name"),r.getString("location_code"),r.getLong("version"),r.getTimestamp("updated_at").toInstant(),r.getString("specification"),r.getString("remark"),r.getString("storage_image_id"),r.getString("sample_image_id"),r.getTimestamp("deleted_at")==null?null:r.getTimestamp("deleted_at").toInstant(),r.getString("snapshot_image_url"),r.getBoolean("image_snapshot_taken"),null,null,null,null,null,null);

    private Where where(Query f){
        require(f.productId().length()<=36&&f.q().length()<=200&&f.location().length()<=100,"查询参数无效");
        require(Set.of("","LINKED","MANUAL").contains(f.source())&&Set.of("","YES","NO").contains(f.photo())&&Set.of("exact","prefix").contains(f.locationMode()),"筛选条件无效");
        StringBuilder sql=new StringBuilder(" FROM sample_location WHERE ").append(f.deleted()?"deletion_key<>''":"deletion_key=''");
        Map<String,Object> params=new HashMap<>();
        if(!f.q().isBlank()){sql.append(" AND LOWER(CONCAT(product_name,' ',sku_name,' ',sku_code,' ',location_code,' ',specification,' ',remark)) LIKE :q ESCAPE '!'");params.put("q","%"+escape(f.q().strip().toLowerCase(Locale.ROOT))+"%");}
        if(!f.productId().isBlank()){sql.append(" AND product_id=:productId");params.put("productId",f.productId());}
        if(!f.source().isEmpty())sql.append(f.source().equals("MANUAL")?" AND variant_id IS NULL":" AND variant_id IS NOT NULL");
        if(!f.photo().isEmpty())sql.append(f.photo().equals("YES")?" AND storage_image_id IS NOT NULL":" AND storage_image_id IS NULL");
        if(!f.location().isBlank()){
            sql.append(f.locationMode().equals("exact")?" AND location_normalized=:location":" AND location_normalized LIKE :location ESCAPE '!'");
            String value=normalize(f.location().strip());params.put("location",f.locationMode().equals("exact")?value:escape(value)+"%");
        }
        return new Where(sql.toString(),params);
    }
    private List<Row> present(List<Row> rows){
        var ids=rows.stream().map(Row::variantId).filter(Objects::nonNull).distinct().toList();
        Map<String,ProductLookup.SamplePresentation> values=new HashMap<>();
        for(var value:products.samplePresentations(ids))values.put(value.variantId(),value);
        return rows.stream().map(r->{
            var current=values.get(r.variantId());
            boolean saved=r.variantId()==null||r.imageSnapshotTaken()||r.sampleImageId()!=null;
            return new Row(r.id(),r.productId(),r.variantId(),r.productName(),r.skuCode(),r.skuName(),r.locationCode(),r.version(),r.updatedAt(),r.specification(),r.remark(),r.storageImageId(),r.sampleImageId(),r.deletedAt(),r.snapshotImageUrl(),r.imageSnapshotTaken(),
                current==null?null:current.productName(),current==null?null:current.skuName(),r.variantId()==null?"MANUAL":current==null?"UNAVAILABLE":current.status(),
                saved?r.sampleImageId():current==null?null:current.imageId(),saved?r.snapshotImageUrl():current==null?null:current.imageUrl(),saved?"SNAPSHOT":"CURRENT_PRODUCT");
        }).toList();
    }
    @GetMapping("/capabilities") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    Map<String,Integer> capabilities(){return Map.of("closeoutVersion",1);}
    @GetMapping @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    Page list(@RequestParam(defaultValue="")String productId,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,
              @RequestParam(defaultValue="")String source,@RequestParam(defaultValue="")String photo,@RequestParam(defaultValue="")String location,@RequestParam(defaultValue="prefix")String locationMode,@RequestParam(defaultValue="false")boolean deleted){
        require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页参数无效");
        var w=where(new Query(q,productId,source,photo,location,locationMode,deleted));
        long count=jdbc.sql("SELECT COUNT(*)"+w.sql()).params(w.params()).query(Long.class).single();
        var rows=jdbc.sql("SELECT *"+w.sql()+" ORDER BY updated_at DESC,id LIMIT :size OFFSET :offset").params(w.params()).param("size",size).param("offset",(long)page*size).query(mapper).list();
        return new Page(present(rows),count,page,size);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    Row detail(@PathVariable String id){return present(List.of(get(id))).getFirst();}
    @GetMapping("/{id}/history") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    AuditHistoryLookup.HistoryPage history(@PathVariable String id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){get(id);require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页参数无效");var result=history.page("SAMPLE_LOCATION",id,page+1,size);return new AuditHistoryLookup.HistoryPage(result.items(),result.total(),page,size);}
    @GetMapping("/locations") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    List<String> locations(@RequestParam(defaultValue="")String q){require(q.length()<=100,"库位查询过长");return jdbc.sql("SELECT MAX(location_code) location_code FROM sample_location WHERE deletion_key='' AND location_normalized LIKE :q ESCAPE '!' GROUP BY location_normalized ORDER BY MAX(updated_at) DESC,location_normalized LIMIT 20").param("q",escape(normalize(q.strip()))+"%").query(String.class).list();}
    @GetMapping("/duplicates") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    List<Row> duplicates(@RequestParam String variantId,@RequestParam String locationCode){require(variantId.length()<=36,"SKU 无效");return present(jdbc.sql("SELECT * FROM sample_location WHERE deletion_key='' AND variant_id=:variant AND location_normalized=:location").param("variant",variantId).param("location",normalize(location(locationCode))).query(mapper).list());}
    @PostMapping("/selection") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    List<Row> selection(@RequestBody Selection input){return present(selected(input.ids()));}
    private List<Row> selected(List<String> ids){
        require(ids!=null&&!ids.isEmpty()&&ids.size()<=500&&ids.stream().allMatch(v->v!=null&&v.length()<=36),"请选择 1～500 条记录");
        var rows=jdbc.sql("SELECT * FROM sample_location WHERE deletion_key='' AND id IN (:ids) ORDER BY updated_at DESC,id").param("ids",ids).query(mapper).list();
        require(rows.size()==new HashSet<>(ids).size(),"部分记录已移除，请刷新后重新选择");return rows;
    }
    record ExportInput(String q,String productId,String source,String photo,String location,String locationMode,List<String> ids){}
    @PostMapping("/export") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    ResponseEntity<byte[]> exportSelected(@RequestBody ExportInput input){
        return export(Objects.requireNonNullElse(input.q(),""),Objects.requireNonNullElse(input.productId(),""),Objects.requireNonNullElse(input.source(),""),Objects.requireNonNullElse(input.photo(),""),Objects.requireNonNullElse(input.location(),""),Objects.requireNonNullElse(input.locationMode(),"prefix"),input.ids()==null||input.ids().isEmpty()?null:input.ids());
    }
    @GetMapping("/export") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    ResponseEntity<byte[]> export(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String productId,@RequestParam(defaultValue="")String source,@RequestParam(defaultValue="")String photo,@RequestParam(defaultValue="")String location,@RequestParam(defaultValue="prefix")String locationMode,@RequestParam(required=false)List<String> ids){
        List<Row> rows;
        if(ids!=null) rows=selected(ids);
        else {var w=where(new Query(q,productId,source,photo,location,locationMode,false));
            rows=jdbc.sql("SELECT *"+w.sql()+" ORDER BY updated_at DESC,id LIMIT 10001").params(w.params()).query(mapper).list();
            require(rows.size()<=10000,"当前结果超过 10000 条，请缩小筛选范围后导出");}
        StringBuilder csv=new StringBuilder("\ufeff品名,规格,来源,所属产品,库位,备注,更新时间（北京时间）\r\n");
        var format=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(java.time.ZoneId.of("Asia/Shanghai"));
        for(var r:rows)csv.append(String.join(",",List.of(cell(r.skuName()),cell(r.specification()),cell(r.variantId()==null?"手动录入":"关联 SKU"),cell(r.productName()),cell(r.locationCode()),cell(r.remark()),cell(format.format(r.updatedAt()))))).append("\r\n");
        return ResponseEntity.ok().header("Content-Type","text/csv; charset=UTF-8").header("Content-Disposition","attachment; filename=sample-stocktake.csv").body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }
    private static String cell(String value){String v=Objects.requireNonNullElse(value,"");if(v.stripLeading().matches("(?s)^[=+@\\-].*")||v.startsWith("\t")||v.startsWith("\r"))v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}

    // Lock one actor before checking/inserting the key: concurrent retries cannot both create a record.
    private Row replay(String key,String operation,Input in,AuthenticatedActor actor){
        if(key==null)return null;
        require(!key.isBlank()&&key.length()<=100,"请求编号无效");
        jdbc.sql("SELECT id FROM iam_user WHERE id=:id FOR UPDATE").param("id",actor.userId()).query(String.class).single();
        return jdbc.sql("SELECT operation,payload_hash,sample_id FROM sample_write_request WHERE actor_id=:actor AND request_id=:key").param("actor",actor.userId()).param("key",key).query((r,n)->{
            if(!operation.equals(r.getString(1))||!hash(in).equals(r.getString(2)))throw new ResponseStatusException(HttpStatus.CONFLICT,"请求编号已用于其他操作，请勿更改待确认请求");
            return get(r.getString(3));
        }).optional().orElse(null);
    }
    private String hash(Input in){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(in)));}catch(Exception e){throw new IllegalStateException(e);}}
    private void remember(String key,String operation,Input in,String id,AuthenticatedActor actor){if(key!=null)jdbc.sql("INSERT INTO sample_write_request(actor_id,request_id,operation,payload_hash,sample_id) VALUES(:actor,:key,:op,:hash,:id)").param("actor",actor.userId()).param("key",key).param("op",operation).param("hash",hash(in)).param("id",id).update();}
    @PostMapping @PreAuthorize("hasAuthority('samples.write')")
    Row create(@RequestBody Input in,@RequestHeader(value="Idempotency-Key",required=false)String key,@AuthenticationPrincipal AuthenticatedActor actor){
        Row replay=replay(key,"CREATE",in,actor);if(replay!=null)return present(List.of(replay)).getFirst();
        String location=location(in.locationCode());boolean manual=in.productId()==null||in.productId().isBlank();
        String productId=null,variantId=null,productName="",code,name;
        Row copy=in.copyFromId()==null?null:active(in.copyFromId());
        if(copy!=null)require(Objects.equals(in.productId(),copy.productId())&&Objects.equals(in.variantId(),copy.variantId()),"复制登记不能改变来源");
        if(manual){require(in.variantId()==null||in.variantId().isBlank(),"手动样品不能关联 SKU");name=text(in.sampleName(),200,true);code=text(in.sampleCode(),64,false);}
        else{var product=products.findById(in.productId()).orElseThrow(()->bad("请选择有效产品"));
            var sku=products.findSku(product.id(),Objects.requireNonNullElse(in.variantId(),"")).orElseThrow(()->bad("请选择该产品下的有效 SKU"));
            productId=product.id();variantId=sku.id();productName=copy==null?product.name():copy.productName();code=copy==null?sku.skuCode():copy.skuCode();name=copy==null?sku.name():copy.skuName();}
        String id=UUID.randomUUID().toString(),photo=photo(in.storageImageId()),samplePhoto=manual?photo(in.sampleImageId()):null,imageUrl=null;
        if(copy!=null&&samplePhoto==null&&copy.sampleImageId()!=null)samplePhoto=images.copyForSample(copy.sampleImageId(),id,actor);
        if(!manual&&samplePhoto==null){
            var current=products.samplePresentations(List.of(variantId)).stream().findFirst().orElse(null);
            if(current!=null){samplePhoto=images.copyForSample(current.imageId(),id,actor);imageUrl=current.imageUrl();}
        }
        if(copy!=null&&copy.imageSnapshotTaken()&&copy.snapshotImageUrl()!=null)imageUrl=copy.snapshotImageUrl();
        jdbc.sql("INSERT INTO sample_location(id,product_id,variant_id,product_name,sku_code,sku_name,location_code,location_normalized,specification,remark,storage_image_id,sample_image_id,snapshot_image_url,image_snapshot_taken) VALUES(:id,:p,:v,:pn,:code,:name,:loc,:norm,:spec,:remark,:photo,:samplePhoto,:url,TRUE)")
            .param("id",id).param("p",productId).param("v",variantId).param("pn",productName).param("code",code).param("name",name).param("loc",location).param("norm",normalize(location))
            .param("spec",text(in.specification(),500,false)).param("remark",text(in.remark(),1000,false)).param("photo",photo).param("samplePhoto",samplePhoto).param("url",imageUrl).update();
        images.attach(photo,CatalogImageGateway.Purpose.SAMPLE_STORAGE,"SAMPLE_LOCATION",id,actor);
        images.attach(samplePhoto,CatalogImageGateway.Purpose.SAMPLE_IMAGE,"SAMPLE_LOCATION",id,actor);
        Row result=get(id);remember(key,"CREATE",in,id,actor);return present(List.of(result)).getFirst();
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('samples.write')")
    Row update(@PathVariable String id,@RequestBody Input in,@RequestHeader(value="Idempotency-Key",required=false)String key,@AuthenticationPrincipal AuthenticatedActor actor){
        Row replay=replay(key,"UPDATE:"+id,in,actor);if(replay!=null)return present(List.of(replay)).getFirst();
        Row before=active(id);String location=location(in.locationCode());
        require(Objects.equals(in.productId(),before.productId())&&Objects.equals(in.variantId(),before.variantId()),"更换样品来源或 SKU 请新增记录");
        require(in.version()!=null&&in.version()>=0,"缺少记录版本");
        String photo=photo(in.storageImageId()),samplePhoto=before.variantId()==null?photo(in.sampleImageId()):before.sampleImageId(),imageUrl=before.snapshotImageUrl();
        if(before.variantId()!=null&&!before.imageSnapshotTaken()){
            var current=products.samplePresentations(List.of(before.variantId())).stream().findFirst().orElse(null);
            if(current!=null){samplePhoto=images.copyForSample(current.imageId(),id,actor);imageUrl=current.imageUrl();}
        }
        int changed=jdbc.sql("UPDATE sample_location SET location_code=:loc,location_normalized=:norm,sku_name=:name,sku_code=:code,specification=:spec,remark=:remark,storage_image_id=:photo,sample_image_id=:samplePhoto,snapshot_image_url=:url,image_snapshot_taken=TRUE,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:v AND deletion_key=''")
            .param("loc",location).param("norm",normalize(location)).param("id",id).param("v",in.version())
            .param("name",before.variantId()==null?text(in.sampleName(),200,true):before.skuName()).param("code",before.variantId()==null?text(in.sampleCode(),64,false):before.skuCode())
            .param("spec",text(in.specification(),500,false)).param("remark",text(in.remark(),1000,false)).param("photo",photo).param("samplePhoto",samplePhoto).param("url",imageUrl).update();
        if(changed!=1)throw conflict();
        images.attach(photo,CatalogImageGateway.Purpose.SAMPLE_STORAGE,"SAMPLE_LOCATION",id,actor);
        images.attach(samplePhoto,CatalogImageGateway.Purpose.SAMPLE_IMAGE,"SAMPLE_LOCATION",id,actor);
        images.releaseReplaced(before.storageImageId(),photo,"SAMPLE_LOCATION",id);
        images.releaseReplaced(before.sampleImageId(),samplePhoto,"SAMPLE_LOCATION",id);
        Row result=get(id);recordLocationChange(actor,before,result);remember(key,"UPDATE:"+id,in,id,actor);return present(List.of(result)).getFirst();
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('samples.remove')")
    void remove(@PathVariable String id,@RequestParam long version,@AuthenticationPrincipal AuthenticatedActor actor){
        active(id);
        int changed=jdbc.sql("UPDATE sample_location SET deleted_at=CURRENT_TIMESTAMP,deleted_by=:actor,deletion_key=id,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:v AND deletion_key=''").param("actor",actor.userId()).param("id",id).param("v",version).update();
        if(changed!=1)throw conflict();
        // Keep attachments active: a soft-deleted sample must remain fully recoverable.
    }
    @PostMapping("/{id}/restore") @PreAuthorize("hasAuthority('samples.remove') and hasAuthority('samples.write')")
    Row restore(@PathVariable String id,@RequestBody Version input,@AuthenticationPrincipal AuthenticatedActor actor){
        Row before=get(id);require(before.deletedAt()!=null,"该样品未移除");
        int changed=jdbc.sql("UPDATE sample_location SET deleted_at=NULL,deleted_by=NULL,deletion_key='',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:v AND deletion_key<>''").param("id",id).param("v",input.version()).update();
        if(changed!=1)throw conflict();Row after=get(id);return present(List.of(after)).getFirst();
    }
    private Row get(String id){return jdbc.sql("SELECT * FROM sample_location WHERE id=:id").param("id",id).query(mapper).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"样品库位记录不存在"));}
    private Row active(String id){var row=get(id);if(row.deletedAt()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"该样品已移除，请刷新列表");return row;}
    private static String escape(String value){return value.replace("!","!!").replace("%","!%").replace("_","!_");}
    private static String normalize(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);}
    private static String location(String value){require(value!=null&&!value.isBlank(),"库位编码必填");String clean=value.strip();require(clean.length()<=100&&normalize(clean).length()<=100&&!clean.chars().anyMatch(Character::isISOControl),"库位编码最多100字，不能包含控制字符");return clean;}
    private static void require(boolean condition,String message){if(!condition)throw bad(message);}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"记录已变化，请加载最新资料并对照草稿后重试");}
    private static String text(String value,int max,boolean required){String clean=value==null?"":value.strip();require((!required||!clean.isEmpty())&&clean.length()<=max&&!clean.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'),"必填项不能为空，字段不能超出长度限制或包含控制字符");return clean;}
    private static String photo(String value){String clean=text(value,36,false);return clean.isEmpty()?null:clean;}

    record Parent(String id,String name){}
    @GetMapping("/parents") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    List<Parent> parents(){return jdbc.sql("SELECT product_id,MAX(product_name) product_name FROM sample_location WHERE product_id IS NOT NULL AND deletion_key='' GROUP BY product_id ORDER BY product_name,product_id").query((r,n)->new Parent(r.getString("product_id"),r.getString("product_name"))).list();}
    @ExceptionHandler(CatalogImageGateway.AttachmentException.class)
    ResponseEntity<Map<String,String>> imageError(CatalogImageGateway.AttachmentException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"detail",e.getMessage()));}
    private void recordLocationChange(AuthenticatedActor actor,Row before,Row after){
        if(Objects.equals(before.locationCode(),after.locationCode()))return;
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),
                "SAMPLE_UPDATE","SAMPLE_LOCATION",after.id(),true,
                Map.of("locationCode",before.locationCode()),Map.of("locationCode",after.locationCode()),"修改样品库位"));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"请求失败")));}
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("code","SAMPLE_LOCATION_EXISTS","detail","该 SKU 已登记在此库位，请查看已有记录或选择其他库位"));}
}
