package com.jiugeng.erp.sample;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.product.ProductLookup;
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
    SampleController(JdbcClient jdbc,ProductLookup products,AuditTrail audit){this.jdbc=jdbc;this.products=products;this.audit=audit;}
    record Input(String productId,String variantId,String locationCode,Long version){}
    record Row(String id,String productId,String variantId,String productName,String skuCode,String skuName,String locationCode,long version,Instant updatedAt){}
    record Page(List<Row> items,long totalCount,int page,int size){}
    private final org.springframework.jdbc.core.RowMapper<Row> mapper=(r,n)->new Row(r.getString("id"),r.getString("product_id"),r.getString("variant_id"),r.getString("product_name"),r.getString("sku_code"),r.getString("sku_name"),r.getString("location_code"),r.getLong("version"),r.getTimestamp("updated_at").toInstant());

    @GetMapping @PreAuthorize("hasAuthority('samples.read')") @Transactional(readOnly=true)
    Page list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        require(q.length()<=200&&page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"查询参数无效");
        String query="%"+q.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        String where=" FROM sample_location WHERE LOWER(CONCAT(product_name,' ',sku_name,' ',sku_code,' ',location_code)) LIKE :q ESCAPE '!'";
        long count=jdbc.sql("SELECT COUNT(*)"+where).param("q",query).query(Long.class).single();
        var rows=jdbc.sql("SELECT *"+where+" ORDER BY updated_at DESC,id LIMIT :size OFFSET :offset").param("q",query).param("size",size).param("offset",(long)page*size).query(mapper).list();
        return new Page(rows,count,page,size);
    }
    @PostMapping @PreAuthorize("hasAuthority('samples.write')")
    Row create(@RequestBody Input in,@AuthenticationPrincipal AuthenticatedActor actor){
        String location=location(in.locationCode());
        var product=products.findById(in.productId()==null?"":in.productId()).orElseThrow(()->bad("请选择有效产品"));
        var sku=products.findSku(product.id(),in.variantId()==null?"":in.variantId()).orElseThrow(()->bad("请选择该产品下的有效 SKU"));
        String id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO sample_location(id,product_id,variant_id,product_name,sku_code,sku_name,location_code,location_normalized) VALUES(:id,:p,:v,:pn,:code,:name,:loc,:norm)")
            .param("id",id).param("p",product.id()).param("v",sku.id()).param("pn",product.name()).param("code",sku.skuCode()).param("name",sku.name()).param("loc",location).param("norm",normalize(location)).update();
        Row result=get(id);record(actor,"SAMPLE_CREATE",null,result);return result;
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('samples.write')")
    Row update(@PathVariable String id,@RequestBody Input in,@AuthenticationPrincipal AuthenticatedActor actor){
        Row before=get(id);String location=location(in.locationCode());
        require(Objects.equals(in.productId(),before.productId())&&Objects.equals(in.variantId(),before.variantId()),"编辑只能修改库位；更换 SKU 请新增记录");
        require(in.version()!=null&&in.version()>=0,"缺少记录版本");
        int changed=jdbc.sql("UPDATE sample_location SET location_code=:loc,location_normalized=:norm,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:v")
            .param("loc",location).param("norm",normalize(location)).param("id",id).param("v",in.version()).update();
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"记录已变化，请刷新后重试");
        Row result=get(id);record(actor,"SAMPLE_UPDATE",before,result);return result;
    }
    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('samples.remove')")
    void remove(@PathVariable String id,@RequestParam long version,@AuthenticationPrincipal AuthenticatedActor actor){
        Row before=get(id);
        int changed=jdbc.sql("DELETE FROM sample_location WHERE id=:id AND version=:v").param("id",id).param("v",version).update();
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"记录已变化，请刷新后重试");
        record(actor,"SAMPLE_REMOVE",before,null);
    }
    private Row get(String id){return jdbc.sql("SELECT * FROM sample_location WHERE id=:id").param("id",id).query(mapper).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"样品库位记录不存在"));}
    private static String normalize(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);}
    private static String location(String value){require(value!=null&&!value.isBlank(),"库位编码必填");String clean=value.strip();require(clean.length()<=100&&normalize(clean).length()<=100&&!clean.chars().anyMatch(Character::isISOControl),"库位编码最多100字，不能包含控制字符");return clean;}
    private static void require(boolean condition,String message){if(!condition)throw bad(message);}
    private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private Map<String,Object> snapshot(Row r){if(r==null)return Map.of();return Map.of("productId",r.productId(),"variantId",r.variantId(),"skuCode",r.skuCode(),"locationCode",r.locationCode(),"version",r.version());}
    private void record(AuthenticatedActor a,String action,Row before,Row after){audit.record(new AuditTrail.BusinessOperation(a.userId(),a.loginName(),a.displayName(),action,"SAMPLE_LOCATION",after==null?before.id():after.id(),true,snapshot(before),snapshot(after),"维护样品库位"));}
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"请求失败")));}
    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","该 SKU 已登记在此库位，请勿重复添加"));}
}
