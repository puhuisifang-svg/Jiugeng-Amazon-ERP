package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.CostTypes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Costing stays with the existing material/BOM owner; no second live BOM is created. */
@Service
class CostService {
    private final JdbcClient jdbc;private final ObjectMapper mapper;private final AuditTrail audit;private final ProductMaterialRelationService relations;
    CostService(JdbcClient jdbc,ObjectMapper mapper,AuditTrail audit,ProductMaterialRelationService relations){this.jdbc=jdbc;this.mapper=mapper;this.audit=audit;this.relations=relations;}
    static MaterialException invalid(String m){return new MaterialException(400,"COST_INVALID",m);}
    static MaterialException conflict(String m){return new MaterialException(409,"COST_CONFLICT",m);}
    String json(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    <T>T read(String s,Class<T> type){try{return mapper.readValue(s,type);}catch(Exception e){throw new IllegalStateException(e);}}
    String hash(Object value){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    static String money(BigDecimal n){return n.setScale(6,RoundingMode.HALF_UP).toPlainString();}
    record Material(String id,String name,String code,String unit,String status,String price,Long priceVersion){}
    record Price(String materialId,String netPrice,String unit,String source,String referenceId,String reason,long version,String updatedAt){}
    record Operation(String id,String name,boolean enabled,long version){}
    record Sku(String id,String productId,String productName,String name,String code,String imageId,boolean active){}
    record Row(Sku sku,Version current,Version draft,boolean stale){}
    record Page(List<Row> items,long totalCount,int page,int size){}
    Sku sku(String id){return jdbc.sql("SELECT v.id,v.product_id,p.name_zh,v.variant_name,v.sku_code,v.main_image_asset_id,v.status vs,p.status ps,v.deleted_at vd,p.deleted_at pd FROM product_variant v JOIN product p ON p.id=v.product_id WHERE v.id=:id").param("id",id).query((r,n)->new Sku(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7).equals("ACTIVE")&&r.getString(8).equals("ACTIVE")&&r.getObject(9)==null&&r.getObject(10)==null)).optional().orElseThrow(()->invalid("SKU不存在"));}
    Material material(String id){return jdbc.sql("SELECT m.id,m.name,m.material_code,m.unit,m.status,c.net_price,c.version,c.unit price_unit FROM auxiliary_material m LEFT JOIN material_standard_cost c ON c.material_id=m.id WHERE m.id=:id").param("id",id).query((r,n)->new Material(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),Objects.equals(r.getString(4),r.getString("price_unit"))&&r.getBigDecimal(6)!=null?r.getBigDecimal(6).toPlainString():null,r.getObject(7)==null?null:r.getLong(7))).optional().orElseThrow(()->invalid("辅料不存在"));}
    Price price(String id){material(id);return jdbc.sql("SELECT * FROM material_standard_cost WHERE material_id=:id").param("id",id).query((r,n)->new Price(id,r.getBigDecimal("net_price").toPlainString(),r.getString("unit"),r.getString("source"),r.getString("reference_id"),r.getString("reason"),r.getLong("version"),r.getString("updated_at"))).optional().orElse(null);}
    record PurchaseReference(String id,String orderNumber,String unit,String price){}
    List<PurchaseReference> purchases(String id){return jdbc.sql("SELECT l.id,o.order_no,l.unit,l.unit_price FROM purchase_order_line l JOIN purchase_order o ON o.id=l.order_id WHERE l.item_id=:id AND o.order_type='MATERIAL' AND o.currency='CNY' AND o.status IN ('SUBMITTED','PARTIALLY_RECEIVED','COMPLETED') ORDER BY o.updated_at DESC,l.id LIMIT 100").param("id",id).query((r,n)->new PurchaseReference(r.getString(1),r.getString(2),r.getString(3),r.getBigDecimal(4).toPlainString())).list();}
    @Transactional
    Price savePrice(String id,PriceInput input,AuthenticatedActor actor){
        jdbc.sql("SELECT id FROM auxiliary_material WHERE id=:id FOR UPDATE").param("id",id).query(String.class).optional().orElseThrow(()->invalid("辅料不存在"));
        var m=material(id);if(!m.status().equals("ACTIVE"))throw invalid("辅料已停用");if(!m.unit().equals(input.unit()))throw invalid("计价单位须与辅料单位一致");
        if(!input.confirmedNet())throw invalid("请明确确认价格为人民币不含税单价");
        var before=price(id);long version=before==null?-1:before.version();if(input.version()!=version)throw conflict("价格已修改，请刷新");
        if(input.source().equals("PURCHASE")){
            var p=purchases(id).stream().filter(v->v.id().equals(input.referenceId())).findFirst().orElseThrow(()->invalid("请选择有效人民币采购明细"));
            if(!p.unit().equals(input.unit())||new BigDecimal(p.price()).compareTo(input.netPrice())!=0)throw invalid("引用采购价格或单位不一致；折算后的价格请按手工核定记录原因");
        }
        String sql=before==null?"INSERT INTO material_standard_cost(material_id,net_price,unit,source,reference_id,reason,version,updated_by) VALUES(:id,:price,:unit,:source,:ref,:reason,0,:actor)":"UPDATE material_standard_cost SET net_price=:price,unit=:unit,source=:source,reference_id=:ref,reason=:reason,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=:actor WHERE material_id=:id";
        jdbc.sql(sql).param("id",id).param("price",input.netPrice()).param("unit",input.unit()).param("source",input.source()).param("ref",input.source().equals("PURCHASE")?input.referenceId():null).param("reason",input.reason()).param("actor",actor.userId()).update();
        record(actor,"COST_MATERIAL_PRICE",id,before,price(id),input.reason());return price(id);
    }
    List<Operation> operations(){return jdbc.sql("SELECT * FROM cost_operation ORDER BY name,id").query((r,n)->new Operation(r.getString("id"),r.getString("name"),r.getBoolean("enabled"),r.getLong("version"))).list();}
    @Transactional
    Operation operation(String id,OperationInput input,AuthenticatedActor actor){
        String target=id==null?UUID.randomUUID().toString():id;
        var before=id==null?null:operations().stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(()->invalid("工序不存在"));
        try{
            if(id==null)jdbc.sql("INSERT INTO cost_operation(id,name,enabled) VALUES(:id,:name,:enabled)").param("id",target).param("name",input.name().trim()).param("enabled",input.enabled()).update();
            else if(jdbc.sql("UPDATE cost_operation SET name=:name,enabled=:enabled,version=version+1 WHERE id=:id AND version=:version").param("id",id).param("name",input.name().trim()).param("enabled",input.enabled()).param("version",input.version()).update()!=1)throw conflict("工序已修改，请刷新");
        }catch(org.springframework.dao.DuplicateKeyException e){throw conflict("工序名称已存在");}
        var result=operations().stream().filter(o->o.id().equals(target)).findFirst().orElseThrow();record(actor,"COST_OPERATION",target,before,result,"维护工序");return result;
    }
    Input initial(String id){
        sku(id);var bom=relations.relations(null,id);return new Input("件",BigDecimal.ONE,bom.stream().filter(ProductMaterialRelationTypes.RelationView::enabled).map(r->new MaterialInput(r.materialId(),r.standardQuantity(),r.unit(),BigDecimal.ONE,BigDecimal.ONE,r.lossRate().movePointRight(2),r.remark())).toList(),List.of(),"","");
    }
    private String sourceHash(String id,Input input){
        var ids=new TreeSet<String>();input.materials().forEach(m->ids.add(m.materialId()));
        var bom=relations.relations(null,id).stream().filter(ProductMaterialRelationTypes.RelationView::enabled).toList();bom.forEach(b->ids.add(b.materialId()));
        var ops=input.labor().stream().map(LaborInput::operationId).collect(java.util.stream.Collectors.toSet());
        return hash(List.of(sku(id),bom,ids.stream().map(this::material).toList(),operations().stream().filter(o->ops.contains(o.id())).toList()));
    }
    private BigDecimal normalized(MaterialInput m,Input i){return m.quantity().multiply(m.conversion()).divide(m.perProducts().multiply(i.baseQuantity()),10,RoundingMode.HALF_UP);}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    Snapshot preview(String id,Input input){
        lockSku(id);lockSources(id,input);
        var s=sku(id);var errors=new ArrayList<String>();if(!s.active())errors.add("SKU或父体已停用/删除");
        if(input.materials().isEmpty()&&(input.noMaterialReason()==null||input.noMaterialReason().isBlank()))errors.add("无物料须填写原因");
        if(input.labor().isEmpty()&&(input.noLaborReason()==null||input.noLaborReason().isBlank()))errors.add("无人工须填写原因");
        var seen=new HashSet<String>();var materials=new ArrayList<MaterialLine>();BigDecimal materialTotal=BigDecimal.ZERO,laborTotal=BigDecimal.ZERO;
        for(var m:input.materials()){
            if(!seen.add(m.materialId()))throw invalid("同一辅料不可重复，请合并用量");var master=material(m.materialId());String error="";
            if(!master.status().equals("ACTIVE"))error="辅料已停用";else if(master.price()==null)error="缺少与当前单位一致的不含税价格";
            if(m.usageUnit().equals(master.unit())&&m.conversion().compareTo(BigDecimal.ONE)!=0)error="相同单位换算系数必须为1";
            if(normalized(m,input).signum()<=0)error="折算用量过小";
            if(normalized(m,input).precision()-normalized(m,input).scale()>14)error="折算用量超出范围";
            BigDecimal amount=master.price()==null?BigDecimal.ZERO:normalized(m,input).multiply(BigDecimal.ONE.add(m.lossPercent().movePointLeft(2))).multiply(new BigDecimal(master.price()));
            if(!error.isEmpty())errors.add(master.name()+"："+error);materialTotal=materialTotal.add(amount);materials.add(new MaterialLine(m,master.name(),master.code(),master.unit(),master.price(),master.priceVersion(),money(amount),error));
        }
        var labor=new ArrayList<LaborLine>();var ops=operations();
        for(var l:input.labor()){
            var op=ops.stream().filter(o->o.id().equals(l.operationId())).findFirst().orElseThrow(()->invalid("工序不存在"));String error=op.enabled()?"":"工序已停用";
            if(!l.basis().equals("BOX")&&l.perProducts().compareTo(BigDecimal.ONE)!=0)error="按件或按批工价的每箱成品数必须为1";
            if(l.price().signum()==0&&(l.remark()==null||l.remark().isBlank()))error="零工价须说明原因";
            var divisor=l.basis().equals("BOX")?l.perProducts():l.basis().equals("BATCH")?input.baseQuantity():BigDecimal.ONE;
            var amount=l.price().multiply(l.times()).divide(divisor,12,RoundingMode.HALF_UP);
            laborTotal=laborTotal.add(amount);labor.add(new LaborLine(l,op.name(),money(amount),error));if(!error.isEmpty())errors.add(op.name()+"："+error);
        }
        return new Snapshot(s.productId(),id,s.productName(),s.name(),s.code(),s.imageId(),input,materials,labor,money(materialTotal),money(laborTotal),money(materialTotal.add(laborTotal)),sourceHash(id,input),errors);
    }
    private void lockSku(String id){
        jdbc.sql("SELECT id FROM product WHERE id=:id FOR UPDATE").param("id",sku(id).productId()).query(String.class).single();
        jdbc.sql("SELECT id FROM product_variant WHERE id=:id FOR UPDATE").param("id",id).query(String.class).optional().orElseThrow(()->invalid("SKU不存在"));
    }
    void lock(String id){
        lockSku(id);
        if(jdbc.sql("SELECT COUNT(*) FROM sku_cost_state WHERE variant_id=:id").param("id",id).query(Long.class).single()==0)jdbc.sql("INSERT INTO sku_cost_state(variant_id) VALUES(:id)").param("id",id).update();
    }
    private void lockSources(String id,Input input){
        var ids=new TreeSet<String>();input.materials().forEach(m->ids.add(m.materialId()));
        relations.relations(null,id).stream().filter(ProductMaterialRelationTypes.RelationView::enabled).forEach(m->ids.add(m.materialId()));
        for(String material:ids)jdbc.sql("SELECT id FROM auxiliary_material WHERE id=:id FOR UPDATE").param("id",material).query(String.class).optional().orElseThrow(()->invalid("辅料不存在"));
        for(String op:input.labor().stream().map(LaborInput::operationId).distinct().sorted().toList())jdbc.sql("SELECT id FROM cost_operation WHERE id=:id FOR UPDATE").param("id",op).query(String.class).optional().orElseThrow(()->invalid("工序不存在"));
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    Version save(String id,Save input,AuthenticatedActor actor){
        lock(id);var snapshot=preview(id,input.input());if(!snapshot.sourceHash().equals(input.sourceHash()))throw conflict("物料、价格或工序已变化，请重新核算并确认");
        String draft=jdbc.sql("SELECT draft_id FROM sku_cost_state WHERE variant_id=:id").param("id",id).query((r,n)->r.getString(1)).optional().orElse(null);
        if(draft!=null){var before=version(draft);if(input.revision()==null||input.revision()!=before.revision())throw conflict("已有成本草稿或草稿已更新，请打开最新草稿");
            jdbc.sql("UPDATE sku_cost_version SET input_json=:input,snapshot_json=:snapshot,source_hash=:hash,reason=:reason,revision=revision+1 WHERE id=:id").param("input",json(input.input())).param("snapshot",json(snapshot)).param("hash",snapshot.sourceHash()).param("reason",input.reason()).param("id",draft).update();
            record(actor,"COST_DRAFT_UPDATE",id,before.snapshot(),snapshot,input.reason());return version(draft);}
        if(input.revision()!=null)throw conflict("草稿状态已变化，请刷新");
        int sequence=jdbc.sql("SELECT sequence_number FROM sku_cost_state WHERE variant_id=:id").param("id",id).query(Integer.class).single()+1;String created=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO sku_cost_version(id,variant_id,version_number,status,input_json,snapshot_json,source_hash,reason,created_by) VALUES(:id,:sku,:num,'DRAFT',:input,:snapshot,:hash,:reason,:actor)").param("id",created).param("sku",id).param("num",sequence).param("input",json(input.input())).param("snapshot",json(snapshot)).param("hash",snapshot.sourceHash()).param("reason",input.reason()).param("actor",actor.userId()).update();
        jdbc.sql("UPDATE sku_cost_state SET draft_id=:draft,sequence_number=:seq WHERE variant_id=:id").param("draft",created).param("seq",sequence).param("id",id).update();record(actor,"COST_DRAFT_CREATE",id,null,snapshot,input.reason());return version(created);
    }
    Version version(String id){return jdbc.sql("SELECT * FROM sku_cost_version WHERE id=:id").param("id",id).query((r,n)->{var input=read(r.getString("input_json"),Input.class);var status=r.getString("status");return new Version(id,r.getString("variant_id"),r.getInt("version_number"),status,input,read(r.getString("snapshot_json"),Snapshot.class),r.getString("source_hash"),r.getLong("revision"),r.getString("reason"),r.getString("created_at"),r.getString("published_at"),!status.equals("HISTORICAL")&&!status.equals("DELETED")&&!r.getString("source_hash").equals(sourceHash(r.getString("variant_id"),input)));}).optional().orElseThrow(()->new MaterialException(404,"COST_NOT_FOUND","成本版本不存在"));}
    List<Version> history(String sku){return jdbc.sql("SELECT id FROM sku_cost_version WHERE variant_id=:id AND status<>'DELETED' ORDER BY version_number DESC").param("id",sku).query(String.class).list().stream().map(this::version).toList();}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    Version publish(String id,Publish input,AuthenticatedActor actor){
        var first=version(id);lock(first.variantId());var before=version(id);if(!before.status().equals("DRAFT")||before.revision()!=input.revision())throw conflict("草稿已变化，请刷新");
        var snapshot=preview(before.variantId(),before.input());if(!snapshot.sourceHash().equals(before.sourceHash()))throw conflict("成本来源已变化，请更新草稿并重新确认");if(!snapshot.errors().isEmpty())throw invalid(String.join("；",snapshot.errors()));
        // Publish normalized usage back to the single existing SKU material relationship.
        relations.replaceVariantAssignments(snapshot.productId(),snapshot.variantId(),snapshot.materials().stream().map(m->new ProductMaterialAssignmentGateway.Assignment(m.input().materialId(),normalized(m.input(),before.input()),m.unit(),m.input().lossPercent().movePointLeft(2),m.input().remark(),true)).toList(),actor);
        String freshHash=sourceHash(before.variantId(),before.input());
        jdbc.sql("UPDATE sku_cost_version SET status='HISTORICAL' WHERE variant_id=:sku AND status='EFFECTIVE'").param("sku",before.variantId()).update();
        jdbc.sql("UPDATE sku_cost_version SET status='EFFECTIVE',source_hash=:hash,revision=revision+1,reason=:reason,published_at=CURRENT_TIMESTAMP WHERE id=:id").param("id",id).param("hash",freshHash).param("reason",input.reason()).update();
        jdbc.sql("UPDATE sku_cost_state SET draft_id=NULL,current_id=:cost WHERE variant_id=:sku").param("cost",id).param("sku",before.variantId()).update();record(actor,"COST_PUBLISH",before.variantId(),null,snapshot,input.reason());return version(id);
    }
    @Transactional
    void delete(String id,long revision,AuthenticatedActor actor){var first=version(id);lock(first.variantId());var v=version(id);if(!v.status().equals("DRAFT")||v.revision()!=revision)throw conflict("仅可删除当前草稿");jdbc.sql("UPDATE sku_cost_version SET status='DELETED',revision=revision+1 WHERE id=:id").param("id",id).update();jdbc.sql("UPDATE sku_cost_state SET draft_id=NULL WHERE variant_id=:id").param("id",v.variantId()).update();record(actor,"COST_DRAFT_DELETE",v.variantId(),v.snapshot(),null,"删除成本草稿");}
    Page list(String q,String productId,String state,int page,int size){
        if(!Set.of("","MISSING","DRAFT","EFFECTIVE","STALE","INCOMPLETE").contains(state))throw invalid("成本状态无效");
        var params=new HashMap<String,Object>();params.put("q","%"+q.trim().replace("!","!!").replace("%","!%").replace("_","!_")+"%");params.put("parent",productId);
        String where=" FROM product_variant v JOIN product p ON p.id=v.product_id LEFT JOIN sku_cost_state s ON s.variant_id=v.id WHERE v.deleted_at IS NULL AND p.deleted_at IS NULL AND (v.variant_name LIKE :q ESCAPE '!' OR v.sku_code LIKE :q ESCAPE '!' OR p.name_zh LIKE :q ESCAPE '!') AND (:parent='' OR p.id=:parent)";
        if(state.equals("MISSING"))where+=" AND s.current_id IS NULL";if(state.equals("DRAFT"))where+=" AND s.draft_id IS NOT NULL";if(state.equals("EFFECTIVE"))where+=" AND s.current_id IS NOT NULL";
        if(state.equals("STALE")||state.equals("INCOMPLETE")){
            var filtered=jdbc.sql("SELECT v.id,s.current_id,s.draft_id"+where+" ORDER BY p.product_code_normalized,v.sku_code_normalized").params(params).query((r,n)->{
                var current=r.getString(2)==null?null:version(r.getString(2));var draft=r.getString(3)==null?null:version(r.getString(3));return new Row(sku(r.getString(1)),current,draft,current!=null&&current.stale());
            }).list().stream().filter(r->state.equals("STALE")?(r.stale()||r.draft()!=null&&r.draft().stale()):!(preview(r.sku().id(),r.draft()!=null?r.draft().input():r.current()!=null?r.current().input():initial(r.sku().id())).errors().isEmpty())).toList();
            return new Page(filtered.stream().skip((long)page*size).limit(size).toList(),filtered.size(),page,size);
        }
        long total=jdbc.sql("SELECT COUNT(*)"+where).params(params).query(Long.class).single();params.put("limit",size);params.put("offset",page*size);
        var rows=jdbc.sql("SELECT v.id,s.current_id,s.draft_id"+where+" ORDER BY p.product_code_normalized,v.sku_code_normalized LIMIT :limit OFFSET :offset").params(params).query((r,n)->{var current=r.getString(2)==null?null:version(r.getString(2));var draft=r.getString(3)==null?null:version(r.getString(3));return new Row(sku(r.getString(1)),current,draft,current!=null&&current.stale());}).list();return new Page(rows,total,page,size);
    }
    void record(AuthenticatedActor actor,String action,String id,Object before,Object after,String reason){audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"PRODUCT_COST",id,true,before==null?null:Map.of("value",before),after==null?null:Map.of("value",after),reason));}
}
