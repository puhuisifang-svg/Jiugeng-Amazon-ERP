package com.jiugeng.erp.cartonlabel;

import static com.jiugeng.erp.cartonlabel.LabelTypes.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LabelService {
    private final JdbcClient jdbc;private final ObjectMapper json;private final AuditTrail audit;private final LabelPdf pdf;
    LabelService(JdbcClient jdbc,ObjectMapper json,AuditTrail audit,LabelPdf pdf) {this.jdbc=jdbc;this.json=json;this.audit=audit;this.pdf=pdf;}
    static Failure invalid(String message) {return new Failure(400,"CARTON_LABEL_INVALID",message);}
    private Failure conflict(String message) {return new Failure(409,"CARTON_LABEL_CONFLICT",message);}
    private Failure missing() {return new Failure(404,"CARTON_LABEL_NOT_FOUND","外箱标模板、发货记录或导出文件不存在");}
    static final class Failure extends RuntimeException {
        final int status;final String code;
        Failure(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
    String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    <T>T decode(String text,Class<T> type) {try{return json.readValue(text,type);}catch(Exception e){throw new IllegalStateException("外箱标存储数据不可读",e);}}
    private String clean(String value) {
        if(value==null)return "";
        if(value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw invalid("字段不能包含控制字符");
        return value.trim();
    }
    @Transactional(readOnly=true)
    List<Template> templates(String customer) {
        return jdbc.sql("SELECT * FROM carton_label_template WHERE customer_id=:id ORDER BY is_default DESC,updated_at DESC,id")
            .param("id",customer).query(this::template).list();
    }
    private Template template(ResultSet r,int n)throws SQLException {
        return new Template(r.getString("id"),r.getString("customer_id"),r.getString("name"),decode(r.getString("config"),Config.class),
            r.getBoolean("enabled"),r.getBoolean("is_default"),r.getLong("version"));
    }
    @Transactional
    Template save(String customer,String id,TemplateInput input,AuthenticatedActor actor) {
        jdbc.sql("SELECT id FROM customer WHERE id=:id FOR UPDATE").param("id",customer).query(String.class).optional().orElseThrow(this::missing);
        if(input.config().rows().stream().map(Row::key).distinct().count()!=4)throw invalid("SKU、数量、箱数、采购人必须各出现一次");
        clean(input.name());clean(input.config().title());clean(input.config().defaultDestination());
        input.config().rows().forEach(row->clean(row.label()));
        if(input.defaultTemplate()&&!input.enabled())throw invalid("默认模板必须启用");
        Template before=id==null?null:templates(customer).stream().filter(t->t.id().equals(id)).findFirst().orElseThrow(this::missing);
        if(before!=null && !Objects.equals(before.version(),input.version()))throw conflict("模板已修改，请重新加载");
        if(id==null && templates(customer).size()>=20)throw invalid("每个客户最多维护 20 个模板");
        if(input.defaultTemplate())jdbc.sql("UPDATE carton_label_template SET is_default=FALSE,version=version+1 WHERE customer_id=:customer AND is_default=TRUE AND id<>:id")
            .param("customer",customer).param("id",id==null?"":id).update();
        String target=id==null?UUID.randomUUID().toString():id;
        var p=new HashMap<String,Object>();p.put("id",target);p.put("customer",customer);p.put("name",clean(input.name()));
        p.put("config",encode(input.config()));p.put("enabled",input.enabled());p.put("default",input.defaultTemplate());p.put("actor",actor.userId());
        if(id==null)jdbc.sql("INSERT INTO carton_label_template(id,customer_id,name,config,enabled,is_default,created_by,updated_by) VALUES(:id,:customer,:name,:config,:enabled,:default,:actor,:actor)").params(p).update();
        else jdbc.sql("UPDATE carton_label_template SET name=:name,config=:config,enabled=:enabled,is_default=:default,version=version+1,updated_by=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id").params(p).update();
        Template after=templates(customer).stream().filter(t->t.id().equals(target)).findFirst().orElseThrow();
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"CARTON_TEMPLATE_SAVE","CUSTOMER",customer,true,
            before==null?null:Map.of("templateId",target,"version",before.version()),Map.of("templateId",target,"version",after.version()),"外箱标模板变更"));
        return after;
    }
    private record Source(String id,long version,String productName,String customerCode,String customerId,String customerName,
                          String cp,int quantity,int boxes,String unit,String date,String status) {}
    private Source source(String id,boolean lock) {
        return jdbc.sql("SELECT * FROM dashboard_today_shipment WHERE id=:id"+(lock?" FOR UPDATE":"")).param("id",id).query((r,n)->{
            var snapshot=decode(Objects.requireNonNullElse(r.getString("customer_snapshot"),"{}"),com.fasterxml.jackson.databind.JsonNode.class);
            return new Source(r.getString("id"),r.getLong("version"),r.getString("product_name_snapshot"),snapshot.path("customerProductCode").asText(),
                snapshot.path("customerId").asText(),snapshot.path("customerName").asText(),r.getString("customer_product_id"),
                r.getInt("quantity"),r.getInt("box_count"),r.getString("quantity_unit"),r.getDate("ship_date").toString(),r.getString("status"));
        }).optional().orElseThrow(this::missing);
    }
    @Transactional(readOnly=true)
    Draft draft(String id) {
        Source s=source(id,false);
        var previous=jdbc.sql("SELECT i.snapshot FROM carton_label_export_item i JOIN carton_label_export e ON e.id=i.export_id WHERE i.shipment_id=:id ORDER BY e.created_at DESC,e.id DESC LIMIT 1")
            .param("id",id).query(String.class).optional().map(text->decode(text,Snapshot.class)).filter(v->Objects.equals(v.customerProductId(),s.cp())).map(Snapshot::entry).orElse(null);
        return new Draft(s.id(),s.version(),s.customerId(),s.customerName(),s.productName(),s.customerCode(),s.quantity(),s.boxes(),s.unit(),templates(s.customerId()),previous);
    }
    private List<Snapshot> snapshots(Batch batch) {
        if(batch.items().stream().map(Entry::shipmentId).distinct().count()!=batch.items().size())throw invalid("不能重复选择同一发货记录");
        var sources=new HashMap<String,Source>();
        // Stable lock ordering across multi-record export requests.
        batch.items().stream().map(Entry::shipmentId).sorted().forEach(id->sources.put(id,source(id,true)));
        var result=new ArrayList<Snapshot>();long pages=0;
        for(var raw:batch.items()) {
            Source s=sources.get(raw.shipmentId());
            if(s.version()!=raw.shipmentVersion())throw conflict("发货记录已修改，请关闭弹窗重新选择："+s.productName());
            if(s.customerId().isBlank()||s.cp()==null)throw invalid("发货记录未绑定客户商品："+s.productName());
            if(s.status().equals("ARCHIVED"))throw conflict("已删除或已转待发货的记录不能生成新外箱标");
            if(s.boxes()<1||s.quantity()<1)throw invalid("发货数量与箱数必须大于零");
            Template t=jdbc.sql("SELECT * FROM carton_label_template WHERE id=:id FOR UPDATE").param("id",raw.templateId()).query(this::template).optional().orElseThrow(this::missing);
            if(!t.customerId().equals(s.customerId())||!t.enabled())throw invalid("请选择该客户启用的外箱标模板");
            if(t.version()!=raw.templateVersion())throw conflict("模板已修改，请重新加载");
            pages+=t.config().singlePage()?1:s.boxes();if(pages>500)throw invalid("单次最多生成 500 张标签，请分批导出");
            Entry e=new Entry(raw.shipmentId(),raw.shipmentVersion(),raw.templateId(),raw.templateVersion(),clean(raw.productName()),
                clean(raw.customerCode()),clean(raw.purchaser()),clean(raw.contractNo()),clean(raw.destination()),clean(raw.carrier()),clean(raw.trackingNo()));
            result.add(new Snapshot(e,s.customerId(),s.customerName(),s.quantity(),s.boxes(),s.unit(),s.date(),s.cp(),t));
        }
        return result;
    }
    @Transactional
    byte[] preview(Batch batch) {return pdf.render(snapshots(batch));}
    byte[] templatePreview(Config config) {
        return pdf.render(List.of(new Snapshot(new Entry("preview",0,"preview",0,"合成产品 黑色 457cm x 20cm",
            "SYN2670","示例采购人","","示例收货地点","示例快递","SYN123456"),
            "preview","示例客户",80,1,"套","2026-01-01","preview",new Template("preview","preview","预览",config,true,true,0))));
    }
    @Transactional
    Export generate(Batch batch,AuthenticatedActor actor) {
        // Actor lock serializes retried request keys; no partial batch or duplicate saved file.
        jdbc.sql("SELECT id FROM iam_user WHERE id=:id FOR UPDATE").param("id",actor.userId()).query(String.class).single();
        String hash;
        try{hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(batch.items()).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException(e);}
        var prior=jdbc.sql("SELECT id,request_hash FROM carton_label_export WHERE created_by=:actor AND request_key=:key")
            .param("actor",actor.userId()).param("key",batch.requestKey()).query((r,n)->List.of(r.getString(1),r.getString(2))).optional();
        if(prior.isPresent()) {if(!hash.equals(prior.get().get(1)))throw conflict("请求内容已变化，请重新生成");return export(prior.get().getFirst());}
        var snapshots=snapshots(batch);byte[] content=pdf.render(snapshots);int pages=snapshots.stream().mapToInt(Snapshot::pages).sum();
        if(content.length>20*1024*1024)throw invalid("PDF 文件过大，请分批导出");
        String id=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO carton_label_export(id,request_key,request_hash,page_count,content,created_by) VALUES(:id,:key,:hash,:pages,:bytes,:actor)")
            .param("id",id).param("key",batch.requestKey()).param("hash",hash).param("pages",pages).param("bytes",content).param("actor",actor.userId()).update();
        for(var s:snapshots)jdbc.sql("INSERT INTO carton_label_export_item(export_id,shipment_id,snapshot) VALUES(:id,:shipment,:snapshot)")
            .param("id",id).param("shipment",s.entry().shipmentId()).param("snapshot",encode(s)).update();
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"CARTON_LABEL_EXPORT","CARTON_LABEL",id,true,null,
            Map.of("pages",pages,"records",snapshots.size()),"生成外箱标 PDF；采购人及物流信息仅存业务快照，不写审计正文"));
        return export(id);
    }
    private Export export(String id) {
        return jdbc.sql("SELECT id,page_count,created_at FROM carton_label_export WHERE id=:id").param("id",id)
            .query((r,n)->new Export(r.getString(1),r.getInt(2),r.getTimestamp(3).toInstant().toString())).optional().orElseThrow(this::missing);
    }
    @Transactional(readOnly=true)
    List<Export> history(String shipment) {
        return jdbc.sql("SELECT e.id,e.page_count,e.created_at FROM carton_label_export e JOIN carton_label_export_item i ON i.export_id=e.id WHERE i.shipment_id=:id ORDER BY e.created_at DESC,e.id DESC LIMIT 30")
            .param("id",shipment).query((r,n)->new Export(r.getString(1),r.getInt(2),r.getTimestamp(3).toInstant().toString())).list();
    }
    @Transactional(readOnly=true)
    byte[] download(String id) {
        return jdbc.sql("SELECT content FROM carton_label_export WHERE id=:id").param("id",id).query(byte[].class).optional().orElseThrow(this::missing);
    }
}
