package com.jiugeng.erp.materialinventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.catalogmedia.CatalogImageGateway;
import com.jiugeng.erp.iam.*;
import com.jiugeng.erp.supplier.SupplierAssignmentGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Aggregate editor and paginated read model. Legacy list remains for existing selectors. */
@RestController @Profile("!mock") @RequestMapping("/api/v1/materials/manage")
@Transactional
class MaterialManagementController {
    private final JdbcClient jdbc;
    private final InventoryRepository inventory;
    private final MaterialService materials;
    private final MaterialRepository repository;
    private final CostService costs;
    private final SupplierAssignmentGateway suppliers;
    private final CatalogImageGateway images;
    private final AuditHistoryLookup history;
    private final AuditTrail audit;
    private final ObjectMapper json;
    MaterialManagementController(JdbcClient jdbc,InventoryRepository inventory,MaterialService materials,MaterialRepository repository,CostService costs,
            SupplierAssignmentGateway suppliers,CatalogImageGateway images,AuditHistoryLookup history,AuditTrail audit,ObjectMapper json){
        this.jdbc=jdbc;this.inventory=inventory;this.materials=materials;this.repository=repository;this.costs=costs;this.suppliers=suppliers;this.images=images;this.history=history;this.audit=audit;this.json=json;
    }
    record Query(String q,String type,String status,String stock,String supplier,String photo,String price){}
    record Where(String sql,Map<String,Object> params){}
    record Row(MaterialTypes.MaterialView material,BigDecimal quantity,BigDecimal safetyStock,boolean initialized,String netPrice,String priceUnit){}
    record Page(List<Row> items,long totalCount,int page,int size){}
    record Detail(MaterialTypes.MaterialView material,CostService.Price price,List<SupplierAssignmentGateway.LinkedSupplier> suppliers,boolean unitLocked,int quantityScale){}
    record Save(@NotBlank @Size(max=100) String requestId,@Size(max=36) String id,Long version,
                @NotNull @Valid MaterialController.MaterialRequest material,
                @Size(max=50) List<@NotNull @Valid Link> suppliers,@Size(max=36) String copyFromId,@Valid CostTypes.CostField cost){}
    record Link(@NotBlank @Size(max=36) String supplierId,@Size(max=500) String purchaseUrl,@Size(max=500) String remark,boolean primary){}
    record Version(@NotNull @Min(0) Long version){}
    record Target(@NotBlank @Size(max=36) String id,@NotNull @Min(0) Long version){}
    record Targets(@NotEmpty @Size(max=100) List<@NotNull @Valid Target> items){}
    record Impact(String id,String name,long products,long purchases,long transactions,BigDecimal quantity,boolean canDelete,boolean unitLocked){}
    record ProductUsage(String productId,String variantId,String productName,String name,BigDecimal quantity,String unit,BigDecimal lossRate,boolean enabled,boolean archived){}
    record Selection(@NotEmpty @Size(max=100) List<@NotBlank @Size(max=36) String> ids){}
    record Export(@NotNull Query filters,@Size(max=100) List<@NotBlank @Size(max=36) String> ids){}
    static boolean allowed(Authentication auth,String permission){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals(permission));}
    static void require(boolean value,String message){if(!value)throw new MaterialException(400,"MATERIAL_INPUT_INVALID",message);}
    static String safe(String value){return value==null?"":value.trim();}
    static String like(String value){return "%"+value.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private Where where(Query f,Authentication auth){
        require(safe(f.q()).length()<=200 && safe(f.type()).length()<=100 && safe(f.supplier()).length()<=128,"查询条件过长");
        require(Set.of("","ACTIVE","DISABLED").contains(safe(f.status()))&&Set.of("","LOW","NORMAL","ZERO","UNINITIALIZED").contains(safe(f.stock()))&&Set.of("","YES","NO").contains(safe(f.photo()))&&Set.of("","MISSING","SET").contains(safe(f.price())),"筛选条件无效");
        if(!safe(f.price()).isEmpty()&&!allowed(auth,"costs.read"))throw new MaterialException(403,"CAPABILITY_DENIED","无成本查看权限");
        if(!safe(f.stock()).isEmpty()&&!allowed(auth,"inventory.read"))throw new MaterialException(403,"CAPABILITY_DENIED","无库存查看权限");
        if(!safe(f.supplier()).isEmpty()&&!allowed(auth,"suppliers.read"))throw new MaterialException(403,"CAPABILITY_DENIED","无供应商查看权限");
        String sql=" FROM auxiliary_material m LEFT JOIN inventory_balance b ON b.auxiliary_material_id=m.id AND b.warehouse_id='00000000-0000-0000-0000-000000000001' LEFT JOIN material_standard_cost c ON c.material_id=m.id WHERE 1=1";
        Map<String,Object> p=new HashMap<>();
        if(!safe(f.q()).isEmpty()){sql+=" AND LOWER(CONCAT(m.name,' ',m.material_code,' ',COALESCE(m.specification,''))) LIKE :q ESCAPE '!'";p.put("q",like(safe(f.q())));}
        if(!safe(f.type()).isEmpty()){sql+=" AND m.material_type=:type";p.put("type",f.type());}
        if(!safe(f.status()).isEmpty()){sql+=" AND m.status=:status";p.put("status",f.status());}
        sql+=switch(safe(f.stock())){case "LOW"->" AND b.quantity<b.safety_stock";case "NORMAL"->" AND b.quantity>=b.safety_stock";case "ZERO"->" AND b.quantity=0";case "UNINITIALIZED"->" AND b.auxiliary_material_id IS NULL";default->"";};
        if(!safe(f.supplier()).isEmpty()){sql+=" AND EXISTS(SELECT 1 FROM auxiliary_material_supplier ms JOIN supplier s ON s.id=ms.supplier_id WHERE ms.auxiliary_material_id=m.id AND LOWER(s.name) LIKE :supplier ESCAPE '!')";p.put("supplier",like(safe(f.supplier())));}
        String image="(m.image_asset_id IS NOT NULL OR m.image_url IS NOT NULL)";
        if(!safe(f.photo()).isEmpty())sql+=" AND "+(f.photo().equals("YES")?image:"NOT "+image);
        if(!safe(f.price()).isEmpty())sql+=f.price().equals("SET")?" AND c.material_id IS NOT NULL AND c.unit=m.unit":" AND (c.material_id IS NULL OR c.unit<>m.unit)";
        return new Where(sql,p);
    }
    private org.springframework.jdbc.core.RowMapper<Row> rowMapper(Authentication auth){return (r,n)->new Row(
        new MaterialTypes.MaterialView(r.getString("id"),r.getString("material_code"),r.getString("name"),r.getString("material_type"),r.getString("specification"),r.getString("unit"),r.getString("image_url"),r.getString("image_asset_id"),r.getString("remark"),MaterialTypes.MaterialStatus.valueOf(r.getString("status")),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getLong("version")),
        allowed(auth,"inventory.read")?r.getBigDecimal("quantity"):null,allowed(auth,"inventory.read")?r.getBigDecimal("safety_stock"):null,allowed(auth,"inventory.read")&&r.getObject("quantity")!=null,
        allowed(auth,"costs.read")&&r.getBigDecimal("net_price")!=null?r.getBigDecimal("net_price").toPlainString():null,allowed(auth,"costs.read")?r.getString("price_unit"):null);}
    private String select(){return "SELECT m.*,b.quantity,b.safety_stock,c.net_price,c.unit price_unit";}
    @GetMapping("/capabilities") @PreAuthorize("hasAuthority('materials.read')") @Transactional(readOnly=true)
    Map<String,Integer> capabilities(){return Map.of("closeoutVersion",1,"simpleCostVersion",1);}
    @GetMapping("/page") @PreAuthorize("hasAuthority('materials.read')") @Transactional(readOnly=true)
    Page page(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String type,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String stock,@RequestParam(defaultValue="") String supplier,@RequestParam(defaultValue="") String photo,@RequestParam(defaultValue="") String price,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
        require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页无效");var w=where(new Query(q,type,status,stock,supplier,photo,price),auth);
        long total=jdbc.sql("SELECT COUNT(*)"+w.sql()).params(w.params()).query(Long.class).single();
        var rows=jdbc.sql(select()+w.sql()+" ORDER BY m.updated_at DESC,m.id DESC LIMIT :size OFFSET :offset").params(w.params()).param("size",size).param("offset",(long)page*size).query(rowMapper(auth)).list();return new Page(rows,total,page,size);
    }
    @PostMapping("/selection") @PreAuthorize("hasAuthority('materials.read')") @Transactional(readOnly=true)
    List<Row> selection(@Valid @RequestBody Selection input,Authentication auth){var w=where(new Query("","","","","","",""),auth);return jdbc.sql(select()+w.sql()+" AND m.id IN (:ids) ORDER BY m.material_code_normalized,m.id").param("ids",input.ids()).query(rowMapper(auth)).list();}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('materials.read')") @Transactional(readOnly=true)
    Detail detail(@PathVariable String id,Authentication auth){return new Detail(materials.material(id),allowed(auth,"costs.read")?costs.price(id):null,allowed(auth,"suppliers.read")?suppliers.materialAssignments(id):List.of(),repository.unitLocked(id),MaterialQuantity.scale(materials.material(id).unit()));}
    private void version(String id,Long version){repository.lock(id);if(version==null||materials.material(id).version()!=version)throw new MaterialException(409,"MATERIAL_VERSION_CONFLICT","辅料已被修改，请对照最新资料后重新保存");}
    private String hash(Object value){try{
        // Preserve the exact old record shape for pending requests created before CostField existed.
        if(value instanceof Save in&&in.cost()==null){
            var legacy=new LinkedHashMap<String,Object>();legacy.put("requestId",in.requestId());legacy.put("id",in.id());legacy.put("version",in.version());legacy.put("material",in.material());legacy.put("suppliers",in.suppliers());legacy.put("copyFromId",in.copyFromId());value=legacy;
        }
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));
    }catch(Exception e){throw new IllegalStateException(e);}}
    @PostMapping("/save") @PreAuthorize("hasAuthority('materials.write') and (#in.suppliers == null or hasAuthority('suppliers.write')) and ((#in.material.costPrice == null and #in.cost == null) or (hasAuthority('costs.read') and hasAuthority('costs.write')))")
    MaterialTypes.MaterialView save(@Valid @RequestBody Save in,@AuthenticationPrincipal AuthenticatedActor actor){
        require(in.cost()==null||in.material().costPrice()==null,"不能同时提交两种成本价字段");
        // Serialize this user's requests, including concurrent retries of a new create.
        jdbc.sql("SELECT id FROM iam_user WHERE id=:id FOR UPDATE").param("id",actor.userId()).query(String.class).single();
        var previous=jdbc.sql("SELECT material_id,payload_hash FROM material_write_request WHERE actor_id=:actor AND request_id=:key").param("actor",actor.userId()).param("key",in.requestId()).query((r,n)->Map.of("id",r.getString(1),"hash",r.getString(2))).optional();
        if(previous.isPresent()){if(!previous.get().get("hash").equals(hash(in)))throw new MaterialException(409,"MATERIAL_REQUEST_CONFLICT","原请求内容不可修改");return materials.material(previous.get().get("id"));}
        boolean create=in.id()==null;
        if(!create)version(in.id(),in.version());
        var before=!create?suppliers.materialAssignments(in.id()):List.<SupplierAssignmentGateway.LinkedSupplier>of();
        var request=in.material();var input=request.input();
        String copyImage=null;
        if(create&&in.copyFromId()!=null){var source=materials.material(in.copyFromId());if(Objects.equals(source.imageAssetId(),input.imageAssetId())){copyImage=source.imageAssetId();input=new MaterialTypes.MaterialInput(input.materialCode(),input.name(),input.materialType(),input.specification(),input.unit(),input.imageUrl(),null,input.remark());}}
        try {
            var saved=create?materials.create(input,actor):materials.update(in.id(),input,actor,in.cost()==null&&request.costPrice()==null);
            if(copyImage!=null){String copied=images.copyForMaterial(copyImage,saved.id(),actor);jdbc.sql("UPDATE auxiliary_material SET image_asset_id=:image WHERE id=:id").param("image",copied).param("id",saved.id()).update();audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"MATERIAL_IMAGE_COPY","AUXILIARY_MATERIAL",saved.id(),true,null,Map.of("imageAssetId",copied),"复制来源辅料图片，独立保存"));}
            if(in.suppliers()!=null){suppliers.replaceMaterialAssignments(saved.id(),in.suppliers().stream().map(l->new SupplierAssignmentGateway.Assignment(l.supplierId(),l.purchaseUrl(),l.remark(),l.primary())).toList(),actor);
                audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"MATERIAL_SUPPLIERS_UPDATE","AUXILIARY_MATERIAL",saved.id(),true,Map.of("suppliers",before),Map.of("suppliers",suppliers.materialAssignments(saved.id())),"更新关联供应商"));}
            if(request.costPrice()!=null)costs.savePrice(saved.id(),request.costPrice(),actor);
            if(in.cost()!=null)costs.saveField(saved.id(),in.cost(),actor);
            jdbc.sql("INSERT INTO material_write_request(actor_id,request_id,operation,payload_hash,material_id) VALUES(:actor,:key,:operation,:hash,:id)").param("actor",actor.userId()).param("key",in.requestId()).param("operation",create?"CREATE":"UPDATE").param("hash",hash(in)).param("id",saved.id()).update();
            return materials.material(saved.id());
        } catch(SupplierAssignmentGateway.AssignmentException e){throw new MaterialException(e.status(),e.code(),e.getMessage());}
          catch(CatalogImageGateway.AttachmentException e){throw new MaterialException(e.status(),e.code(),e.getMessage());}
    }
    @GetMapping("/{id}/impact") @PreAuthorize("hasAuthority('materials.read')") @Transactional(readOnly=true)
    Impact impact(@PathVariable String id){var m=materials.material(id);long products=count("SELECT COUNT(*) FROM product_auxiliary_material WHERE auxiliary_material_id=:id",id),purchases=count("SELECT COUNT(*) FROM purchase_order_line l JOIN purchase_order p ON p.id=l.order_id WHERE p.order_type='MATERIAL' AND l.item_id=:id",id),transactions=count("SELECT COUNT(*) FROM inventory_transaction WHERE auxiliary_material_id=:id",id);
        var q=jdbc.sql("SELECT COALESCE(SUM(quantity),0) FROM inventory_balance WHERE auxiliary_material_id=:id").param("id",id).query(BigDecimal.class).single();
        return new Impact(id,m.name(),products,purchases,transactions,q,!repository.isReferenced(id)&&q.signum()==0,repository.unitLocked(id));}
    private long count(String sql,String id){return jdbc.sql(sql).param("id",id).query(Long.class).single();}
    @PostMapping("/{id}/status") @PreAuthorize("hasAuthority('materials.write')")
    MaterialTypes.MaterialView status(@PathVariable String id,@Valid @RequestBody Version v,@AuthenticationPrincipal AuthenticatedActor actor){version(id,v.version());return materials.material(id).status()==MaterialTypes.MaterialStatus.ACTIVE?materials.disable(id,actor):materials.enable(id,actor);}
    @PostMapping("/delete") @PreAuthorize("hasAuthority('materials.delete')")
    void delete(@Valid @RequestBody Targets targets,@AuthenticationPrincipal AuthenticatedActor actor){var ordered=targets.items().stream().sorted(Comparator.comparing(Target::id)).toList();for(var target:ordered){version(target.id(),target.version());if(!impact(target.id()).canDelete())throw new MaterialException(409,"MATERIAL_IN_USE",impact(target.id()).name()+" 已有产品、采购、库存或成本版本引用，本次未删除任何辅料");}materials.delete(ordered.stream().map(Target::id).toList(),actor);}
    @GetMapping("/{id}/history") @PreAuthorize("hasAuthority('materials.read')") @Transactional(readOnly=true)
    AuditHistoryLookup.HistoryPage history(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,Authentication auth){
        materials.material(id);require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页无效");
        var scopes=new ArrayList<AuditHistoryLookup.HistoryScope>();
        scopes.add(new AuditHistoryLookup.HistoryScope("AUXILIARY_MATERIAL",null));
        if(allowed(auth,"inventory.read"))scopes.add(new AuditHistoryLookup.HistoryScope("INVENTORY","INVENTORY_SAFETY_STOCK_UPDATE"));
        var result=history.page(scopes,id,page+1,size);
        if(allowed(auth,"costs.read"))return result;
        var items=result.items().stream().map(e->e.action().equals("MATERIAL_COST_UPDATE")?new AuditHistoryLookup.HistoryEvent(e.id(),e.action(),e.actorLoginName(),e.actorDisplayName(),null,null,"成本价已修改（无成本查看权限）",e.occurredAt()):e).toList();
        return new AuditHistoryLookup.HistoryPage(items,result.total(),result.page(),result.size());
    }
    @GetMapping("/{id}/products") @PreAuthorize("hasAuthority('materials.read') and hasAuthority('products.read')") @Transactional(readOnly=true)
    Map<String,Object> products(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页无效");materials.material(id);
        String from=" FROM product_auxiliary_material r JOIN product p ON p.id=r.product_id JOIN product_variant v ON v.id=r.variant_id WHERE r.auxiliary_material_id=:id";
        var rows=jdbc.sql("SELECT r.*,p.name_zh,v.variant_name,p.deleted_at pd,v.deleted_at vd"+from+" ORDER BY p.name_zh,v.variant_name,r.id LIMIT :size OFFSET :offset").param("id",id).param("size",size).param("offset",(long)page*size).query((r,n)->new ProductUsage(r.getString("product_id"),r.getString("variant_id"),r.getString("name_zh"),r.getString("variant_name"),r.getBigDecimal("standard_quantity"),r.getString("unit"),r.getBigDecimal("loss_rate"),r.getBoolean("enabled"),r.getObject("pd")!=null||r.getObject("vd")!=null)).list();
        return Map.of("items",rows,"totalCount",count("SELECT COUNT(*)"+from,id));}
    @GetMapping("/{id}/inventory") @PreAuthorize("hasAuthority('inventory.read')") @Transactional(readOnly=true)
    Map<String,Object> inventory(@PathVariable String id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        materials.material(id);require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页无效");var result=new HashMap<String,Object>();result.put("balance",inventory.balance(id).orElse(null));result.put("ledger",inventory.page(id,page,size));return result;
    }
    @PostMapping(value="/export",produces="text/csv;charset=UTF-8") @PreAuthorize("hasAuthority('materials.read')") @Transactional(readOnly=true)
    ResponseEntity<byte[]> export(@Valid @RequestBody Export input,Authentication auth){var w=where(input.filters(),auth);String sql=w.sql();Map<String,Object> params=new HashMap<>(w.params());if(input.ids()!=null&&!input.ids().isEmpty()){sql+=" AND m.id IN (:ids)";params.put("ids",input.ids());}
        long total=jdbc.sql("SELECT COUNT(*)"+sql).params(params).query(Long.class).single();require(total<=10000,"一次最多导出 10000 条，请缩小筛选范围");
        var rows=jdbc.sql(select()+sql+" ORDER BY m.material_code_normalized,m.id").params(params).query(rowMapper(auth)).list();boolean stock=allowed(auth,"inventory.read"),price=allowed(auth,"costs.read");
        var out=new StringBuilder("\uFEFF名称,类型,规格,单位,状态");if(stock)out.append(",现存量,安全库存");if(price)out.append(",人民币不含税成本价");out.append("\r\n");
        for(var row:rows){var m=row.material();var cells=new ArrayList<String>(Arrays.asList(m.name(),m.materialType(),m.specification(),m.unit(),m.status()==MaterialTypes.MaterialStatus.ACTIVE?"启用":"停用"));if(stock){cells.add(row.initialized()?row.quantity().toPlainString():"未建账");cells.add(row.initialized()?row.safetyStock().toPlainString():"");}if(price)cells.add(Objects.equals(row.priceUnit(),m.unit())?row.netPrice():"未维护");out.append(cells.stream().map(MaterialManagementController::csv).collect(java.util.stream.Collectors.joining(","))).append("\r\n");}
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=materials.csv").body(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static String csv(String value){String s=value==null?"":value;if(!s.stripLeading().isEmpty()&&"=+@-".indexOf(s.stripLeading().charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
}
