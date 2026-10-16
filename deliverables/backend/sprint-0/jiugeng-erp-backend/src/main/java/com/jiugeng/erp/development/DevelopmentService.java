package com.jiugeng.erp.development;

import static com.jiugeng.erp.development.DevelopmentTypes.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuditHistoryLookup;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.product.ProductLookup;
import com.jiugeng.erp.selection.CandidateApproved;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("!mock")
class DevelopmentService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final AuditTrail audit;
    private final AuditHistoryLookup history;
    private final ProductLookup products;
    DevelopmentService(JdbcClient jdbc,ObjectMapper mapper,AuditTrail audit,AuditHistoryLookup history,ProductLookup products) {
        this.jdbc=jdbc; this.mapper=mapper; this.audit=audit; this.history=history; this.products=products;
    }

    @EventListener
    @Transactional(propagation=Propagation.MANDATORY)
    public void approved(CandidateApproved event) {
        if (jdbc.sql("SELECT COUNT(*) FROM product_development WHERE candidate_id=:id")
                .param("id",event.candidateId()).query(Long.class).single()>0) return;
        jdbc.sql("""
                INSERT INTO product_development(id,candidate_id,number,name,developer,source_asin,source_title,
                    source_category,source_score,source_price,source_url,source_reason,fields_json)
                VALUES(:id,:id,:number,:title,:owner,:asin,:title,:category,:score,:price,:url,:reason,'{}')
                """).param("id",event.candidateId()).param("number","ND-"+event.asin()).param("title",event.title())
                .param("owner",event.owner()).param("asin",event.asin()).param("category",event.category())
                .param("score",event.score()).param("price",event.referencePrice()).param("url",event.sourceUrl())
                .param("reason",event.selectionReason()).update();
        record(event.actor(),"DEVELOPMENT_CREATE",get(event.candidateId(),false),"候选通过，生成新品开发草稿");
    }

    @Transactional(readOnly=true)
    Page list(Status status,String q,int page,int size) {
        if(page<0 || page>100000 || !Set.of(20,50,100).contains(size)) throw invalid("分页参数不正确");
        String search=text(q,300);
        String where=" WHERE 1=1";
        if(status!=null) where+=" AND status=:status";
        if(search!=null) where+=" AND (LOWER(number) LIKE :q OR LOWER(name) LIKE :q OR LOWER(source_asin) LIKE :q OR LOWER(COALESCE(developer,'')) LIKE :q)";
        var count=jdbc.sql("SELECT COUNT(*) FROM product_development"+where);
        var rows=jdbc.sql("SELECT * FROM product_development"+where+" ORDER BY updated_at DESC,id LIMIT :size OFFSET :offset")
                .param("size",size).param("offset",(long)page*size);
        if(status!=null){ count=count.param("status",status.name()); rows=rows.param("status",status.name()); }
        if(search!=null){ String pattern="%"+search.toLowerCase(java.util.Locale.ROOT)+"%"; count=count.param("q",pattern); rows=rows.param("q",pattern); }
        return new Page(rows.query(this::map).list(),count.query(Long.class).single(),page,size);
    }

    @Transactional(readOnly=true)
    Detail detail(String id) {
        View item=get(id,false);
        var product=item.fields().linkedProductId()==null ? null : products.findById(item.fields().linkedProductId()).orElse(null);
        return new Detail(item,history.find("PRODUCT_DEVELOPMENT",id),product);
    }

    @Transactional
    View update(String id,Update input,AuthenticatedActor actor) {
        View before=get(id,true); version(input.version(),before);
        if(before.status()==Status.COMPLETED||before.status()==Status.VOID) throw conflict("已完成或已作废的需求不可修改");
        Fields fields=validate(input.fields());
        if(before.status()==Status.IN_PROGRESS) ready(fields);
        jdbc.sql("""
                UPDATE product_development SET name=:name,developer=:developer,fields_json=:fields,
                  version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id
                """).param("name",fields.name()).param("developer",fields.developer()).param("fields",json(fields))
                .param("id",id).update();
        View after=get(id,false); record(actor,"DEVELOPMENT_UPDATE",after,"保存新品开发资料"); return after;
    }

    @Transactional
    View transition(String id,Transition input,AuthenticatedActor actor) {
        View before=get(id,true); version(input.version(),before);
        Status target=input.target();
        boolean allowed=(before.status()==Status.DRAFT&&(target==Status.IN_PROGRESS||target==Status.VOID))
                ||(before.status()==Status.IN_PROGRESS&&(target==Status.COMPLETED||target==Status.VOID));
        if(!allowed) throw conflict("不允许该状态转换，已完成/已作废需求为只读");
        String reason=text(input.reason(),500);
        if(target==Status.VOID&&reason==null) throw invalid("作废原因必填");
        if(target!=Status.VOID&&reason!=null) throw invalid("只有作废操作可填写作废原因");
        if(target==Status.IN_PROGRESS) ready(before.fields());
        if(target==Status.COMPLETED) {
            if(before.fields().sampleNotes()==null) throw invalid("完成前请填写打样/验收结论");
            if(before.fields().linkedProductId()==null) throw invalid("完成前请关联已启用的产品档案");
            activeProduct(before.fields().linkedProductId());
        }
        jdbc.sql("UPDATE product_development SET status=:status,void_reason=:reason,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("status",target.name()).param("reason",reason).param("id",id).update();
        View after=get(id,false); record(actor,"DEVELOPMENT_"+target.name(),after,"新品开发状态变更"); return after;
    }

    private View get(String id,boolean lock) {
        return jdbc.sql("SELECT * FROM product_development WHERE id=:id"+(lock?" FOR UPDATE":""))
                .param("id",id).query(this::map).optional()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"新品开发不存在；仅审核通过的候选可进入"));
    }
    private View map(ResultSet rs,int row) throws SQLException {
        Source source=new Source(rs.getString("candidate_id"),rs.getString("source_asin"),rs.getString("source_title"),
                rs.getString("source_category"),rs.getInt("source_score"),rs.getBigDecimal("source_price"),
                rs.getString("source_url"),rs.getString("source_reason"));
        Fields fields;
        try {
            String data=rs.getString("fields_json");
            fields=data.equals("{}") ? new Fields(rs.getString("name"),rs.getString("developer"),null,0,null,null,null,
                    source.title(),null,source.category(),null,null,null,null,null,null,null,null,null,null,null,null)
                    : mapper.readValue(data,Fields.class);
        } catch(JsonProcessingException error){throw new IllegalStateException("Invalid persisted development data",error);}
        return new View(rs.getString("id"),rs.getString("number"),Status.valueOf(rs.getString("status")),rs.getLong("version"),
                source,fields,rs.getString("void_reason"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
    }
    private Fields validate(Fields f) {
        if(f==null)throw invalid("开发资料不能为空");
        String name=text(f.name(),300); if(name==null)throw invalid("需求名称必填");
        int stars=f.stars()==null?0:f.stars(); if(stars<0||stars>5)throw invalid("推荐星级为 0 至 5");
        for(LocalDate date:new LocalDate[]{f.startDate(),f.endDate(),f.launchDate()})
            if(date!=null&&(date.getYear()<2000||date.getYear()>9999))throw invalid("日期超出有效范围");
        if(f.startDate()!=null&&f.endDate()!=null&&f.endDate().isBefore(f.startDate()))throw invalid("开发结束日期不能早于开始日期");
        if(f.endDate()!=null&&f.launchDate()!=null&&f.launchDate().isBefore(f.endDate()))throw invalid("开售日期不能早于开发结束日期");
        String productId=text(f.linkedProductId(),36); if(productId!=null)activeProduct(productId);
        return new Fields(name,text(f.developer(),100),text(f.buyer(),100),stars,f.startDate(),f.endDate(),f.launchDate(),
                text(f.productName(),300),text(f.brand(),100),text(f.category(),100),text(f.plannedSku(),100),
                text(f.material(),200),number(f.purchaseCost()),number(f.weight()),number(f.length()),number(f.width()),number(f.height()),
                text(f.supplyNotes(),3000),text(f.sampleNotes(),3000),text(f.researchNotes(),3000),text(f.remark(),3000),productId);
    }
    private void ready(Fields fields) {
        if(fields.developer()==null||fields.productName()==null||fields.startDate()==null||fields.endDate()==null)
            throw invalid("开始开发前请填写开发员、计划品名、开始及结束日期");
    }
    private void activeProduct(String id) {
        if(products.findById(id).filter(ProductLookup.ProductReference::active).isEmpty())
            throw invalid("关联产品不存在或未启用");
    }
    private void version(Long value,View current) {
        if(value==null||value<0)throw invalid("版本号必填");
        if(value!=current.version())throw conflict("资料已被更新，请刷新后重试，当前输入不会自动覆盖");
    }
    private BigDecimal number(BigDecimal value) {
        if(value!=null&&(value.signum()<0||value.scale()>4||value.compareTo(new BigDecimal("9999999999.9999"))>0))
            throw invalid("数值必须非负、最多四位小数且不超过 9999999999.9999");
        return value;
    }
    private String text(String value,int max) {
        if(value==null||value.trim().isEmpty())return null;
        String result=value.trim();
        if(result.length()>max||result.chars().anyMatch(c->c<32&&c!='\n'&&c!='\r'&&c!='\t'))throw invalid("字段长度或格式不正确");
        return result;
    }
    private String json(Fields fields) {
        try{return mapper.writeValueAsString(fields);}catch(JsonProcessingException error){throw new IllegalStateException(error);}
    }
    private void record(AuthenticatedActor actor,String action,View item,String detail) {
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,
                "PRODUCT_DEVELOPMENT",item.id(),true,null,Map.of("status",item.status(),"version",item.version(),
                "candidateId",item.source().candidateId()),detail));
    }
    private ResponseStatusException invalid(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
