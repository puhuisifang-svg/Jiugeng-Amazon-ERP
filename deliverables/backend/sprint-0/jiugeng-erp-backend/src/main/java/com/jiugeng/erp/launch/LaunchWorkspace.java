package com.jiugeng.erp.launch;

import static com.jiugeng.erp.launch.LaunchRules.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.product.ProductLookup;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Shared versioned workspace. Identity creation and approval events stay in the owning modules. */
@Service @Transactional
public class LaunchWorkspace {
    public record Command(Long version,String action,String reason,boolean selfConfirmed) {}
    public record Save(Long version,JsonNode document) {}
    public record View(String kind,String id,String state,int stage,long version,JsonNode document,JsonNode baseline,JsonNode proposal,JsonNode riskReview,List<String> issues,Object calculation,List<Revision> history) {}
    public record Revision(long version,String action,String actor,String reason,String date,JsonNode snapshot) {}
    private final JdbcClient jdbc; private final ObjectMapper json; private final ProductLookup products;
    public LaunchWorkspace(JdbcClient jdbc,ObjectMapper json,ProductLookup products){this.jdbc=jdbc;this.json=json;this.products=products;}
    public boolean exists(String kind,String id){return jdbc.sql("SELECT COUNT(*) FROM launch_workspace WHERE kind=:k AND entity_id=:id").param("k",kind).param("id",id).query(Long.class).single()>0;}
    public void legacyGuard(String kind,String id){if(exists(kind,id))throw new ResponseStatusException(HttpStatus.CONFLICT,"该记录已使用新版工作台，请在工作台执行评估、编辑及审批");}
    public JsonNode blank(){return json.createObjectNode();}
    public void create(String kind,String id,String state,JsonNode seed,JsonNode baseline){
        if(exists(kind,id))return;
        jdbc.sql("INSERT INTO launch_workspace(kind,entity_id,state,document_json,baseline_json) VALUES(:k,:id,:s,:d,:b)")
            .param("k",kind).param("id",id).param("s",state).param("d",encode(seed)).param("b",baseline==null?null:encode(baseline)).update();
    }
    public JsonNode decode(String value){try{return value==null?json.createObjectNode():json.readTree(value);}catch(Exception e){throw new IllegalStateException("Invalid stored workspace",e);}}
    public String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    @Transactional(readOnly=true)
    public View get(String kind,String id){return read(kind,id,false);}
    private View read(String kind,String id,boolean lock){
        return jdbc.sql("SELECT * FROM launch_workspace WHERE kind=:k AND entity_id=:id"+(lock?" FOR UPDATE":""))
            .param("k",kind).param("id",id).query((r,n)->{
                JsonNode d=decode(r.getString("document_json")),review=decode(r.getString("risk_review_json"));List<String> issues=new ArrayList<>(evaluationIssues(d,kind.equals("CANDIDATE")));
                if(!review.path("valid").asBoolean())issues.add("风险调查尚未经审核人员复核");
                Object calculation=null;try{calculation=cost(d);}catch(ResponseStatusException ignored){}
                return new View(kind,id,r.getString("state"),r.getInt("stage"),r.getLong("version"),d,decode(r.getString("baseline_json")),decode(r.getString("proposal_json")),review,issues,calculation,history(kind,id));
            }).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"工作台不存在"));
    }
    private List<Revision> history(String kind,String id){return jdbc.sql("SELECT * FROM launch_revision WHERE kind=:k AND entity_id=:id ORDER BY version DESC").param("k",kind).param("id",id).query((r,n)->new Revision(r.getLong("version"),r.getString("action"),r.getString("actor_name"),r.getString("reason"),r.getTimestamp("created_at").toInstant().toString(),decode(r.getString("snapshot_json")))).list();}
    private void version(View v,Long expected){require(expected!=null,"版本号必填");if(v.version()!=expected)throw new ResponseStatusException(HttpStatus.CONFLICT,"资料已更新，请重新载入对比；输入未覆盖他人修改");}
    public View save(String kind,String id,Save input,AuthenticatedActor actor){
        View v=read(kind,id,true);version(v,input.version());
        require(Set.of("NEW","EVALUATING","RETURNED","DRAFT","ACTIVE").contains(v.state()),"当前状态只读，请先撤回或恢复");
        require(v.proposal().isEmpty(),"请先处理待审核变更");validate(input.document());
        ObjectNode d=input.document().deepCopy();
        boolean changed=!fingerprint(v.document()).equals(fingerprint(d));
        boolean riskChanged=changed||!v.document().path("risks").equals(d.path("risks"))||!v.document().path("riskVersion").equals(d.path("riskVersion"));
        if(kind.equals("DEVELOPMENT")&&v.state().equals("ACTIVE")&&changed){
            require(!text(d,"changeReason").isBlank(),"修改执行方案需填写变更原因，保存后提交审核");
            jdbc.sql("UPDATE launch_workspace SET proposal_json=:d WHERE kind=:k AND entity_id=:id").param("d",encode(d)).param("k",kind).param("id",id).update();
            append(v,"CHANGE_SUBMIT",text(d,"changeReason"),actor,v.document());return get(kind,id);
        }
        jdbc.sql("UPDATE launch_workspace SET document_json=:d,risk_review_json=:r WHERE kind=:k AND entity_id=:id")
            .param("d",encode(d)).param("r",riskChanged?null:encode(v.riskReview())).param("k",kind).param("id",id).update();
        append(v,"SAVE",null,actor,d);return get(kind,id);
    }
    private JsonNode fingerprint(JsonNode d){ObjectNode n=json.createObjectNode();for(String key:List.of("market","brand","customer","scenario","specs","requirements","price","costs","commission","advertising","loss","investment","budget","endDate","selectedQuote"))n.set(key,d.path(key));
        ArrayNode requirements=json.createArrayNode();for(JsonNode row:d.path("requirements")){ObjectNode r=json.createObjectNode();for(String f:List.of("id","needId","proposal","priority","standard"))r.set(f,row.path(f));requirements.add(r);}n.set("requirements",requirements);
        for(JsonNode quote:d.path("quotes"))if(text(quote,"id").equals(text(d,"selectedQuote"))){ObjectNode q=json.createObjectNode();for(String f:List.of("supplier","amount","currency","moq","basis"))q.set(f,quote.path(f));n.set("selectedQuoteValues",q);}return n;}
    public View act(String kind,String id,Command c,boolean reviewer,AuthenticatedActor actor){
        View v=read(kind,id,true);version(v,c.version());String action=c.action(),state=v.state(),next=state;int stage=v.stage();ObjectNode d=v.document().deepCopy();
        require(action!=null,"操作必填");
        Set<String> admin=Set.of("REVIEW_RISK","APPROVE","RETURN","REJECT","CHANGE_APPROVE","CHANGE_REJECT","COMPLETE","TERMINATE");
        if(admin.contains(action)&&!reviewer)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"该操作需要审核权限");
        require(!Set.of("APPROVED","COMPLETED","VOID").contains(state),"已完成记录只读");
        if(Set.of("APPROVE","COMPLETE","CHANGE_APPROVE").contains(action)){
            require(!clean(c.reason()).isBlank(),"审核结论必填");
            String submitter=action.startsWith("CHANGE")?lastActor(kind,id,"CHANGE_SUBMIT"):lastActor(kind,id,"SUBMIT");
            if(actor.userId().equals(submitter))require(c.selfConfirmed(),"自审请明确确认");
        }
        switch(action){
            case "START" -> {
                if(kind.equals("CANDIDATE")){require(state.equals("NEW")||state.equals("RETURNED"),"当前不可开始评估");next="EVALUATING";}
                else{require(state.equals("DRAFT")&&!v.baseline().isEmpty(),"请先完成立项审批");
                    for(String f:List.of("owner","startDate","endDate"))require(!text(d,f).isBlank(),"开始开发需要负责人和计划起止日");validate(d);next="ACTIVE";}
            }
            case "REVIEW_RISK" -> {
                require(Set.of("EVALUATING","RETURNED","NEW","DRAFT","ACTIVE").contains(state),"当前状态不可复核");
                List<String> issues=riskIssues(d);require(issues.isEmpty(),String.join("；",issues));require(!clean(c.reason()).isBlank(),"复核意见必填");
                ObjectNode r=json.createObjectNode().put("valid",true).put("actor",actor.displayName()).put("actorId",actor.userId()).put("date",Instant.now().toString()).put("reason",c.reason());
                jdbc.sql("UPDATE launch_workspace SET risk_review_json=:r WHERE kind=:k AND entity_id=:id").param("r",encode(r)).param("k",kind).param("id",id).update();
            }
            case "SUBMIT" -> {
                if(kind.equals("CANDIDATE")){require(Set.of("EVALUATING","RETURNED").contains(state),"请先开始评估");ready(v);next="REVIEW";}
                else if(state.equals("DRAFT")){require(v.baseline().isEmpty(),"来源候选已批准，无需重复立项");ready(v);next="PROJECT_REVIEW";}
                else{require(state.equals("ACTIVE")&&stage==4,"请完成开发阶段再送验收");completion(v);next="FINAL_REVIEW";}
            }
            case "WITHDRAW" -> {require(Set.of("REVIEW","PROJECT_REVIEW","FINAL_REVIEW").contains(state),"当前没有待审核版本");require(reviewer||actor.userId().equals(lastActor(kind,id,"SUBMIT")),"仅提交人或审核人可撤回");next=kind.equals("CANDIDATE")?"EVALUATING":state.equals("PROJECT_REVIEW")?"DRAFT":"ACTIVE";}
            case "APPROVE" -> {require(state.equals("REVIEW")||state.equals("PROJECT_REVIEW"),"当前无待立项审批");ready(v);next=kind.equals("CANDIDATE")?"APPROVED":"DRAFT";baseline(v,d);}
            case "RETURN" -> {require(Set.of("REVIEW","PROJECT_REVIEW","FINAL_REVIEW").contains(state),"当前无待审版本");reason(c);next=kind.equals("CANDIDATE")?"RETURNED":state.equals("PROJECT_REVIEW")?"DRAFT":"ACTIVE";}
            case "REJECT" -> {require(kind.equals("CANDIDATE")&&!state.equals("REJECTED"),"当前不可淘汰");reason(c);next="REJECTED";}
            case "REOPEN" -> {require(state.equals("REJECTED"),"仅淘汰候选可重新评估");reason(c);next="EVALUATING";}
            case "NEXT" -> {require(kind.equals("DEVELOPMENT")&&state.equals("ACTIVE")&&stage<4,"当前不可推进");stageReady(v);stage++;}
            case "BACK" -> {require(kind.equals("DEVELOPMENT")&&state.equals("ACTIVE")&&stage>0,"当前不可回退");reason(c);stage--;}
            case "PAUSE" -> {require(kind.equals("DEVELOPMENT")&&Set.of("DRAFT","ACTIVE").contains(state),"当前不可暂停");reason(c);jdbc.sql("UPDATE launch_workspace SET resume_state=:s WHERE kind=:k AND entity_id=:id").param("s",state).param("k",kind).param("id",id).update();next="PAUSED";}
            case "RESUME" -> {require(state.equals("PAUSED"),"项目未暂停");reason(c);next=jdbc.sql("SELECT resume_state FROM launch_workspace WHERE kind=:k AND entity_id=:id").param("k",kind).param("id",id).query(String.class).single();}
            case "TERMINATE" -> {require(kind.equals("DEVELOPMENT"),"操作不适用");reason(c);next="VOID";}
            case "CHANGE_APPROVE","CHANGE_REJECT" -> {require(kind.equals("DEVELOPMENT")&&state.equals("ACTIVE")&&!v.proposal().isEmpty(),"没有待审核变更");reason(c);
                if(action.equals("CHANGE_APPROVE")){d=v.proposal().deepCopy();d.put("specVersion","r"+(v.version()+1));d.put("riskVersion","");d.put("acceptedChange",Instant.now().toString());stage=0;
                    jdbc.sql("UPDATE launch_workspace SET document_json=:d,risk_review_json=NULL WHERE kind=:k AND entity_id=:id").param("d",encode(d)).param("k",kind).param("id",id).update();}
                jdbc.sql("UPDATE launch_workspace SET proposal_json=NULL WHERE kind=:k AND entity_id=:id").param("k",kind).param("id",id).update();}
            case "COMPLETE" -> {require(kind.equals("DEVELOPMENT")&&state.equals("FINAL_REVIEW"),"请先提交完成审核");completion(v);next="COMPLETED";}
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"未知操作");
        }
        jdbc.sql("UPDATE launch_workspace SET state=:s,stage=:stage WHERE kind=:k AND entity_id=:id").param("s",next).param("stage",stage).param("k",kind).param("id",id).update();
        append(v,action,c.reason(),actor,d);return get(kind,id);
    }
    private String clean(String s){return s==null?"":s.trim();}
    private void reason(Command c){require(!clean(c.reason()).isBlank(),"请填写操作原因");require(c.reason().length()<=2000,"原因过长");}
    private String lastActor(String kind,String id,String action){return jdbc.sql("SELECT actor_id FROM launch_revision WHERE kind=:k AND entity_id=:id AND action=:a ORDER BY version DESC LIMIT 1").param("k",kind).param("id",id).param("a",action).query(String.class).optional().orElse("");}
    private void baseline(View v,JsonNode d){jdbc.sql("UPDATE launch_workspace SET baseline_json=:d WHERE kind=:k AND entity_id=:id").param("d",encode(d)).param("k",v.kind()).param("id",v.id()).update();}
    private void ready(View v){require(v.issues().isEmpty(),String.join("；",v.issues()));require(v.proposal().isEmpty(),"请先处理待审核变更");}
    /** Read-only UI guidance using the exact gates used by transitions. Never advances a project. */
    public Map<String,Object> developmentReadiness(String id){
        View v=get("DEVELOPMENT",id);String action="";List<String> issues=new ArrayList<>();
        if(v.state().equals("DRAFT")) action=v.baseline().isEmpty()?"SUBMIT":"START";
        else if(v.state().equals("ACTIVE")) action=v.stage()<4?"NEXT":"SUBMIT";
        else if(v.state().equals("PROJECT_REVIEW")) action="APPROVE";
        else if(v.state().equals("FINAL_REVIEW")) action="COMPLETE";
        try{
            if(!v.proposal().isEmpty())issues.add("请先处理待审核方案变更");
            if(action.equals("START")){for(String f:List.of("owner","startDate","endDate"))if(text(v.document(),f).isBlank())issues.add(switch(f){case "owner"->"请填写开发负责人";case "startDate"->"请填写计划开始日";default->"请填写计划完成日";});validate(v.document());}
            else if(action.equals("NEXT"))stageReady(v);
            else if(action.equals("COMPLETE")||(action.equals("SUBMIT")&&v.state().equals("ACTIVE")))completion(v);
            else if(!action.isEmpty())issues.addAll(v.issues());
        }catch(ResponseStatusException e){issues.add(e.getReason());}
        return Map.of("action",action,"issues",issues,"version",v.version());
    }
    private void stageReady(View v){
        JsonNode d=v.document();require(v.proposal().isEmpty(),"请先处理待审核变更");
        if(v.stage()==0){require(!d.path("requirements").isEmpty()&&!d.path("specs").isEmpty(),"请完善方案及开发要求");for(JsonNode r:d.path("requirements"))for(String f:List.of("proposal","standard","owner"))require(!text(r,f).isBlank(),"要求须有内容、验收标准、负责人");}
        if(v.stage()>=1){JsonNode quote=selected(d,"quotes","selectedQuote");for(String f:List.of("supplier","amount","currency","basis","validUntil","specVersion"))require(!text(quote,f).isBlank(),"选定报价需完整且有有效期/方案版本");require(text(quote,"specVersion").equals(text(d,"specVersion")),"报价与当前方案版本不一致");require(!date(text(quote,"validUntil")).isBefore(LocalDate.now()),"报价已过期，请确认后另存报价");require(!d.path("samples").isEmpty(),"请登记收到的样品及验证记录");}
        if(v.stage()>=2){JsonNode sample=selected(d,"samples","selectedSample");require(!text(sample,"receivedDate").isBlank()&&!text(sample,"tester").isBlank(),"最终样品需实际到样日和测试人");require(text(sample,"result").equals("PASS")&&!text(sample,"evidence").isBlank(),"最终样品须通过且有证据");
            require(text(sample,"specVersion").equals(text(d,"specVersion")),"样品与当前方案版本不一致，请复测");
            require(!d.path("requirements").isEmpty(),"开发要求不能为空");
            for(JsonNode r:d.path("requirements")){if(text(r,"priority").equals("MUST"))require(text(r,"result").equals("PASS")&&!text(r,"evidence").isBlank()&&text(r,"testedVersion").equals(text(d,"specVersion")),"必须要求需通过当前方案版本的验收并提供证据");else require(!text(r,"disposition").isBlank(),"建议及后续要求须有处置说明");}}
        if(v.stage()>=3){require(v.riskReview().path("valid").asBoolean(),"定型风险须经审核人员复核");require(riskIssues(d).isEmpty(),String.join("；",riskIssues(d)));require(text(d,"riskVersion").equals(text(d,"specVersion")),"风险调查与当前方案版本不一致");}
    }
    private JsonNode selected(JsonNode d,String rows,String key){String id=text(d,key);for(JsonNode row:d.path(rows))if(!id.isBlank()&&text(row,"id").equals(id))return row;throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选定"+(key.equals("selectedQuote")?"供应报价":"最终样品"));}
    private void completion(View v){stageReady(v);cost(v.document());JsonNode d=v.document();
        String product=text(d,"productId");require(products.findById(product).filter(ProductLookup.ProductReference::active).isPresent(),"关联商品不存在或未启用");require(!d.path("skus").isEmpty(),"至少关联一个目标SKU");
        for(JsonNode sku:d.path("skus"))require(products.findSku(product,text(sku,"skuId")).filter(ProductLookup.SkuReference::active).isPresent(),"目标SKU不属于商品或未启用");
        require(!d.path("handover").isEmpty(),"请填写交接清单");for(JsonNode row:d.path("handover"))require(!text(row,"name").isBlank()&&!text(row,"evidence").isBlank()&&!text(row,"owner").isBlank(),"交接项需名称、依据及责任人");
    }
    private void append(View v,String action,String reason,AuthenticatedActor actor,JsonNode document){
        jdbc.sql("UPDATE launch_workspace SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE kind=:k AND entity_id=:id").param("k",v.kind()).param("id",v.id()).update();
        ObjectNode snapshot=json.createObjectNode();snapshot.set("document",document);snapshot.put("state",get(v.kind(),v.id()).state());snapshot.put("stage",get(v.kind(),v.id()).stage());
        jdbc.sql("INSERT INTO launch_revision(id,kind,entity_id,version,action,actor_id,actor_name,reason,snapshot_json) VALUES(:uuid,:k,:id,:v,:a,:actor,:name,:reason,:snapshot)")
            .param("uuid",UUID.randomUUID().toString()).param("k",v.kind()).param("id",v.id()).param("v",v.version()+1).param("a",action).param("actor",actor.userId()).param("name",actor.displayName()==null?actor.loginName():actor.displayName()).param("reason",reason).param("snapshot",encode(snapshot)).update();
    }
}
