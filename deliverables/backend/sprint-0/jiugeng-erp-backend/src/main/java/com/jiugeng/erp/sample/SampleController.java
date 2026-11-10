package com.jiugeng.erp.sample;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.product.ProductLookup;
import com.jiugeng.erp.catalogmedia.CatalogImageGateway;
import java.util.*;
import java.time.Instant;
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
    private final CatalogImageGateway images;
    SampleController(JdbcClient jdbc,ProductLookup products,AuditTrail audit,CatalogImageGateway images){this.jdbc=jdbc;this.products=products;this.audit=audit;this.images=images;}
    record Input(String productId,String variantId,String locationCode,Long version,String sampleName,String sampleCode,String specification,String remark,String storageImageId,String sampleImageId){}
    record Row(String id,String productId,String variantId,String productName,String skuCode,String skuName,String locationCode,long version,Instant updatedAt,String specification,String remark,String storageImageId,String sampleImageId){}
    record Page(List<Row> items,long totalCount,int page,int size){}
    private final org.springframework.jdbc.core.RowMapper<Row> mapper=(r,n)->new Row(r.getString("id"),r.getString("product_id"),r.getString("variant_id"),r.getString("product_name"),r.getString("sku_code"),r.getString("sku_name"),r.getString("location_code"),r.getLong("version"),r.getTimestamp("updated_at").toInstant(),r.getString("specification"),r.getString("remark"),r.getString("storage_image_id"),r.getString("sample_image_id"));

    @GetMapping @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    Page list(@RequestParam(defaultValue="")String productId,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        require(productId.length()<=36&&q.length()<=200&&page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"查询参数无效");
        String query="%"+q.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" FROM sample_location WHERE LOWER(CONCAT(product_name,' ',sku_name,' ',sku_code,' ',location_code)) LIKE :q ESCAPE '!' AND (:productId='' OR product_id=:productId)";
        long count=jdbc.sql("SELECT COUNT(*)"+where).param("q",query).param("productId",productId).query(Long.class).single();
        var rows=jdbc.sql("SELECT *"+where+" ORDER BY updated_at DESC,id LIMIT :size OFFSET :offset").param("q",query).param("productId",productId).param("size",size).param("offset",(long)page*size).query(mapper).list();
        return new Page(rows,count,page,size);
    }
    @PostMapping @PreAuthorize("hasAuthority('samples.write')")
    Row create(@RequestBody Input in,@AuthenticationPrincipal AuthenticatedActor actor){
        String location=location(in.locationCode());
        boolean manual=in.productId()==null||in.productId().isBlank();
        String productId=null,variantId=null,productName="",code,name;
        if(manual){require(in.variantId()==null||in.variantId().isBlank(),"手动样品不能关联 SKU");name=text(in.sampleName(),200,true);code=text(in.sampleCode(),64,false);}
        else{var product=products.findById(in.productId()).orElseThrow(()->bad("请选择有效产品"));
            var sku=products.findSku(product.id(),in.variantId()==null?"":in.variantId()).orElseThrow(()->bad("请选择该产品下的有效 SKU"));
            productId=product.id();variantId=sku.id();productName=product.name();code=sku.skuCode();name=sku.name();}
        String id=UUID.randomUUID().toString(),photo=photo(in.storageImageId()),samplePhoto=photo(in.sampleImageId());
        jdbc.sql("INSERT INTO sample_location(id,product_id,variant_id,product_name,sku_code,sku_name,location_code,location_normalized,specification,remark,storage_image_id,sample_image_id) VALUES(:id,:p,:v,:pn,:code,:name,:loc,:norm,:spec,:remark,:photo,:samplePhoto)")
            .param("id",id).param("p",productId).param("v",variantId).param("pn",productName).param("code",code).param("name",name).param("loc",location).param("norm",normalize(location))
            .param("spec",text(in.specification(),500,false)).param("remark",text(in.remark(),1000,false)).param("photo",photo).param("samplePhoto",samplePhoto).update();
        images.attach(photo,CatalogImageGateway.Purpose.SAMPLE_STORAGE,"SAMPLE_LOCATION",id,actor);
        images.attach(samplePhoto,CatalogImageGateway.Purpose.SAMPLE_IMAGE,"SAMPLE_LOCATION",id,actor);
        Row result=get(id);record(actor,"SAMPLE_CREATE",null,result);return result;
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('samples.write')")
    Row update(@PathVariable String id,@RequestBody Input in,@AuthenticationPrincipal AuthenticatedActor actor){
        Row before=get(id);String location=location(in.locationCode());
        require(Objects.equals(in.productId(),before.productId())&&Objects.equals(in.variantId(),before.variantId()),"更换样品来源或 SKU 请新增记录");
        require(in.version()!=null&&in.version()>=0,"缺少记录版本");
        String photo=photo(in.storageImageId()),samplePhoto=photo(in.sampleImageId());
        int changed=jdbc.sql("UPDATE sample_location SET location_code=:loc,location_normalized=:norm,sku_name=:name,sku_code=:code,specification=:spec,remark=:remark,storage_image_id=:photo,sample_image_id=:samplePhoto,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:v")
            .param("loc",location).param("norm",normalize(location)).param("id",id).param("v",in.version())
            .param("name",before.variantId()==null?text(in.sampleName(),200,true):before.skuName()).param("code",before.variantId()==null?text(in.sampleCode(),64,false):before.skuCode())
            .param("spec",text(in.specification(),500,false)).param("remark",text(in.remark(),1000,false)).param("photo",photo).param("samplePhoto",samplePhoto).update();
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"记录已变化，请刷新后重试");
        images.attach(photo,CatalogImageGateway.Purpose.SAMPLE_STORAGE,"SAMPLE_LOCATION",id,actor);
        images.attach(samplePhoto,CatalogImageGateway.Purpose.SAMPLE_IMAGE,"SAMPLE_LOCATION",id,actor);
        images.releaseReplaced(before.storageImageId(),photo,"SAMPLE_LOCATION",id);
        images.releaseReplaced(before.sampleImageId(),samplePhoto,"SAMPLE_LOCATION",id);
        Row result=get(id);record(actor,"SAMPLE_UPDATE",before,result);return result;
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('samples.remove')")
    void remove(@PathVariable String id,@RequestParam long version,@AuthenticationPrincipal AuthenticatedActor actor){
        Row before=get(id);
        int changed=jdbc.sql("DELETE FROM sample_location WHERE id=:id AND version=:v").param("id",id).param("v",version).update();
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"记录已变化，请刷新后重试");
        images.releaseReplaced(before.storageImageId(),null,"SAMPLE_LOCATION",id);
        images.releaseReplaced(before.sampleImageId(),null,"SAMPLE_LOCATION",id);
        record(actor,"SAMPLE_REMOVE",before,null);
    }
    private Row get(String id){return jdbc.sql("SELECT * FROM sample_location WHERE id=:id").param("id",id).query(mapper).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"样品库位记录不存在"));}
    private static String normalize(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);}
    private static String location(String value){require(value!=null&&!value.isBlank(),"库位编码必填");String clean=value.strip();require(clean.length()<=100&&normalize(clean).length()<=100&&!clean.chars().anyMatch(Character::isISOControl),"库位编码最多100字，不能包含控制字符");return clean;}
    private static void require(boolean condition,String message){if(!condition)throw bad(message);}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static String text(String value,int max,boolean required){String clean=value==null?"":value.strip();require((!required||!clean.isEmpty())&&clean.length()<=max&&!clean.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'),"样品名称必填，且字段不能超出长度限制或包含控制字符");return clean;}
    private static String photo(String value){String clean=text(value,36,false);return clean.isEmpty()?null:clean;}
    private Map<String,Object> snapshot(Row r){if(r==null)return Map.of();Map<String,Object> value=new LinkedHashMap<>();value.put("productId",r.productId());value.put("variantId",r.variantId());value.put("sampleName",r.skuName());value.put("sampleCode",r.skuCode());value.put("locationCode",r.locationCode());value.put("storageImageId",r.storageImageId());value.put("sampleImageId",r.sampleImageId());value.put("specification",r.specification());value.put("remark",r.remark());value.put("version",r.version());return value;}
    record Parent(String id,String name){}
    @GetMapping("/parents") @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    List<Parent> parents(){return jdbc.sql("SELECT product_id,MAX(product_name) product_name FROM sample_location WHERE product_id IS NOT NULL GROUP BY product_id ORDER BY product_name,product_id").query((r,n)->new Parent(r.getString("product_id"),r.getString("product_name"))).list();}
    @ExceptionHandler(CatalogImageGateway.AttachmentException.class)
    ResponseEntity<Map<String,String>> imageError(CatalogImageGateway.AttachmentException e){return ResponseEntity.status(e.status()).body(Map.of("detail",e.getMessage()));}
    private void record(AuthenticatedActor a,String action,Row before,Row after){audit.record(new AuditTrail.BusinessOperation(a.userId(),a.loginName(),a.displayName(),action,"SAMPLE_LOCATION",after==null?before.id():after.id(),true,snapshot(before),snapshot(after),"维护样品库位"));}
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"请求失败")));}
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","该 SKU 已登记在此库位，请勿重复添加"));}
}
