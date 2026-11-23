package com.jiugeng.erp.supplier;

import static com.jiugeng.erp.supplier.SupplierTypes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.catalogmedia.CatalogImageGateway;
import com.jiugeng.erp.iam.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/suppliers/manage") @Transactional
class SupplierManagementController {
    private final SupplierService service; private final SupplierRepository repo; private final JdbcClient jdbc;
    private final CatalogImageGateway images; private final AuditHistoryLookup history; private final ObjectMapper json;
    SupplierManagementController(SupplierService service,SupplierRepository repo,JdbcClient jdbc,CatalogImageGateway images,AuditHistoryLookup history,ObjectMapper json){this.service=service;this.repo=repo;this.jdbc=jdbc;this.images=images;this.history=history;this.json=json;}
    record Save(@NotBlank @Size(max=100) String requestId,@Size(max=36) String id,@NotNull @Valid SupplierController.Request supplier,@Size(max=36) String copyFromId,boolean impactConfirmed){}
    record State(@NotNull Status status,@Min(0) long version,boolean impactConfirmed){}
    record Impact(Map<String,Long> counts,long total,long restrictedCount,Long drafts,Long pendingReceipts){}
    record Relations(List<Dependency> items,long totalCount,int page,int size){}
    record Export(@Size(max=128) String q,Status status,Category category,@Size(max=100) List<@NotBlank @Size(max=36) String> ids){}
    private static boolean allowed(Authentication a,String p){return a.getAuthorities().stream().anyMatch(v->v.getAuthority().equals(p));}
    private static String permission(String type){return switch(type){case "PRODUCT"->"products.read";case "MATERIAL"->"materials.read";default->"purchases.read";};}
    private static void require(boolean value,String message){if(!value)throw new SupplierException(400,"SUPPLIER_INPUT_INVALID",message);}
    @GetMapping("/capabilities") @PreAuthorize("hasAuthority('suppliers.read')")
    Map<String,Integer> capabilities(){return Map.of("closeoutVersion",1);}
    private long count(String sql,String id){return jdbc.sql(sql).param("id",id).query(Long.class).single();}
    @GetMapping("/{id}/impact") @PreAuthorize("hasAuthority('suppliers.read')") @Transactional(readOnly=true)
    Impact impact(@PathVariable String id,Authentication auth){service.get(id);Map<String,Long> all=new LinkedHashMap<>();all.put("PRODUCT",count("SELECT COUNT(*) FROM product_variant_supplier WHERE supplier_id=:id",id));all.put("MATERIAL",count("SELECT COUNT(*) FROM auxiliary_material_supplier WHERE supplier_id=:id",id));all.put("PURCHASE",count("SELECT COUNT(*) FROM purchase_order WHERE supplier_id=:id",id));Map<String,Long> visible=new LinkedHashMap<>();long restricted=0,total=0;for(var entry:all.entrySet()){total+=entry.getValue();if(allowed(auth,permission(entry.getKey())))visible.put(entry.getKey(),entry.getValue());else restricted+=entry.getValue();}return new Impact(visible,total,restricted,allowed(auth,"purchases.read")?count("SELECT COUNT(*) FROM purchase_order WHERE supplier_id=:id AND status='DRAFT'",id):null,allowed(auth,"purchases.read")?count("SELECT COUNT(*) FROM purchase_order WHERE supplier_id=:id AND status IN ('SUBMITTED','PARTIALLY_RECEIVED')",id):null);}
    private String relationFrom(String type){return switch(type){case "PRODUCT"->" FROM product_variant_supplier r JOIN product_variant v ON v.id=r.variant_id JOIN product p ON p.id=v.product_id WHERE r.supplier_id=:id";case "MATERIAL"->" FROM auxiliary_material_supplier r JOIN auxiliary_material m ON m.id=r.auxiliary_material_id WHERE r.supplier_id=:id";default->" FROM purchase_order p WHERE p.supplier_id=:id";};}
    @GetMapping("/{id}/relations") @PreAuthorize("hasAuthority('suppliers.read')") @Transactional(readOnly=true)
    Relations relations(@PathVariable String id,@RequestParam String type,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){service.get(id);require(Set.of("PRODUCT","MATERIAL","PURCHASE").contains(type)&&page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页或关联类型无效");if(!allowed(auth,permission(type)))throw new SupplierException(403,"CAPABILITY_DENIED","无关联业务查看权限");String from=relationFrom(type);String fields=switch(type){case "PRODUCT"->"v.id,p.id parent_id,v.sku_code code,CONCAT(p.name_zh,' / ',v.variant_name) label,CASE WHEN p.deleted_at IS NOT NULL OR v.deleted_at IS NOT NULL THEN 1 ELSE 0 END archived";case "MATERIAL"->"m.id,m.id parent_id,m.material_code code,m.name label,0 archived";default->"p.id,p.id parent_id,p.order_no code,p.status label,0 archived";};var rows=jdbc.sql("SELECT "+fields+from+" ORDER BY code,id LIMIT :size OFFSET :offset").param("id",id).param("size",size).param("offset",(long)page*size).query((r,n)->new Dependency(type,r.getString("id"),r.getString("parent_id"),r.getString("code"),r.getString("label"),true,r.getBoolean("archived"))).list();return new Relations(rows,count("SELECT COUNT(*)"+from,id),page,size);}
    @GetMapping("/{id}/history") @PreAuthorize("hasAuthority('suppliers.read')") @Transactional(readOnly=true)
    AuditHistoryLookup.HistoryPage history(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){service.get(id);require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页无效");return history.page("SUPPLIER",id,page+1,size);}
    private void version(View before,Long version){if(version==null||before.version()!=version)throw new SupplierException(409,"SUPPLIER_VERSION_CONFLICT","供应商已被修改，请对照最新资料后重新保存");}
    private boolean impactful(View before,SupplierController.Request next){return (before.status()==Status.ACTIVE&&next.status()==Status.DISABLED)||!next.categories().containsAll(before.categories());}
    private String hash(Object input){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input)));}catch(Exception e){throw new IllegalStateException(e);}}
    @PostMapping("/save") @PreAuthorize("hasAuthority('suppliers.write')")
    View save(@Valid @RequestBody Save input,@AuthenticationPrincipal AuthenticatedActor actor){jdbc.sql("SELECT id FROM iam_user WHERE id=:id FOR UPDATE").param("id",actor.userId()).query(String.class).single();var previous=jdbc.sql("SELECT supplier_id,payload_hash FROM supplier_write_request WHERE actor_id=:actor AND request_id=:key").param("actor",actor.userId()).param("key",input.requestId()).query((r,n)->Map.of("id",r.getString(1),"hash",r.getString(2))).optional();if(previous.isPresent()){if(!previous.get().get("hash").equals(hash(input)))throw new SupplierException(409,"SUPPLIER_REQUEST_CONFLICT","原请求内容不可修改");return service.get(previous.get().get("id"));}
        var request=input.supplier();boolean create=input.id()==null;if(!create){repo.lock(input.id());var before=service.get(input.id());version(before,request.version());if(impactful(before,request)&&!input.impactConfirmed())throw new SupplierException(409,"SUPPLIER_IMPACT_CONFIRM_REQUIRED","请先确认停用或分类变更对关联业务的影响");}
        var command=request.input();String copyImage=null;if(create&&input.copyFromId()!=null){var source=service.get(input.copyFromId());if(source.qrImageId()!=null&&Objects.equals(source.qrImageId(),command.qrImageId())){copyImage=source.qrImageId();command=new Input(command.name(),command.contactPerson(),command.phone(),command.wechatId(),command.sourceUrl(),command.qrImageUrl(),null,command.remark(),command.status(),command.version(),command.categories());}}
        try{View saved;
            if(create&&copyImage!=null){String id=UUID.randomUUID().toString();String copied=images.copyForSupplier(copyImage,id,actor);command=new Input(command.name(),command.contactPerson(),command.phone(),command.wechatId(),command.sourceUrl(),command.qrImageUrl(),copied,command.remark(),command.status(),command.version(),command.categories());saved=service.createWithId(id,command,actor);}else saved=create?service.create(command,actor):service.update(input.id(),command,actor);
            jdbc.sql("INSERT INTO supplier_write_request(actor_id,request_id,payload_hash,supplier_id) VALUES(:actor,:key,:hash,:id)").param("actor",actor.userId()).param("key",input.requestId()).param("hash",hash(input)).param("id",saved.id()).update();return saved;
        }catch(CatalogImageGateway.AttachmentException e){throw new SupplierException(e.status(),e.code(),e.getMessage());}}
    @PostMapping("/{id}/status") @PreAuthorize("hasAuthority('suppliers.write')")
    View status(@PathVariable String id,@Valid @RequestBody State input,@AuthenticationPrincipal AuthenticatedActor actor){repo.lock(id);var before=service.get(id);version(before,input.version());if(input.status()==Status.DISABLED&&!input.impactConfirmed())throw new SupplierException(409,"SUPPLIER_IMPACT_CONFIRM_REQUIRED","请先确认停用影响");return service.update(id,new Input(before.name(),before.contactPerson(),before.phone(),before.wechatId(),before.sourceUrl(),before.qrImageUrl(),before.qrImageId(),before.remark(),input.status(),input.version(),before.categories()),actor);}
    @GetMapping("/duplicates") @PreAuthorize("hasAuthority('suppliers.read')") @Transactional(readOnly=true)
    List<View> duplicates(@RequestParam(defaultValue="") @Size(max=128) String name,@RequestParam(defaultValue="") @Size(max=32) String phone,@RequestParam(defaultValue="") @Size(max=100) String wechat,@RequestParam(defaultValue="") String exclude){var ids=jdbc.sql("SELECT id FROM supplier WHERE id<>:exclude AND ((:name<>'' AND LOWER(name)=LOWER(:name)) OR (:phone<>'' AND phone=:phone) OR (:wechat<>'' AND LOWER(wechat_id)=LOWER(:wechat))) ORDER BY name,id LIMIT 10").param("exclude",exclude).param("name",name.trim()).param("phone",phone.trim()).param("wechat",wechat.trim()).query(String.class).list();return ids.stream().map(service::get).toList();}
    @PostMapping(value="/export",produces="text/csv;charset=UTF-8") @PreAuthorize("hasAuthority('suppliers.read')") @Transactional(readOnly=true)
    ResponseEntity<byte[]> export(@Valid @RequestBody Export input){List<View> rows;if(input.ids()!=null&&!input.ids().isEmpty())rows=input.ids().stream().distinct().map(service::get).toList();else{require(repo.count(input.q(),input.status(),input.category())<=10000,"一次最多导出 10000 条，请缩小筛选范围");rows=repo.list(input.q(),input.status(),input.category(),0,10000);}StringBuilder csv=new StringBuilder("\uFEFF名称,分类,联系人,电话,微信,1688链接,状态,备注\r\n");for(var row:rows)csv.append(Arrays.asList(row.name(),row.categories().stream().map(v->v==Category.MATERIAL?"辅料":"产品").collect(java.util.stream.Collectors.joining("、")),row.contactPerson(),row.phone(),row.wechatId(),row.sourceUrl(),row.status()==Status.ACTIVE?"启用":"停用",row.remark()).stream().map(SupplierManagementController::csv).collect(java.util.stream.Collectors.joining(","))).append("\r\n");return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=suppliers.csv").body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static String csv(String value){String s=value==null?"":value;String trimmed=s.stripLeading();if(!trimmed.isEmpty()&&"=+@-".indexOf(trimmed.charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
