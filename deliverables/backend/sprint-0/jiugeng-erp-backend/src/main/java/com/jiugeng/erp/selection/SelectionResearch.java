package com.jiugeng.erp.selection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class SelectionResearch {
    record Criterion(Integer score, String reason) {}
    record Research(Long version, String customer, String differentiation, String supply,
                    String riskStatus, String riskReason, List<Criterion> criteria, String estimateId, String conclusion) {}
    record Cost(String name, BigDecimal amount, String currency, BigDecimal exchangeRate, String basis) {}
    record EstimateInput(Long version, String requestId, String name, BigDecimal price, String currency,
                         List<Cost> costs, BigDecimal commissionRate, BigDecimal advertisingRate,
                         BigDecimal lossRate, LocalDate rateDate, String basis) {}
    record Result(BigDecimal unitCost, BigDecimal profit, BigDecimal margin, BigDecimal breakEven,
                  BigDecimal lowPriceProfit, BigDecimal highCostProfit) {}
    record Estimate(String id, int revision, JsonNode input, JsonNode result, String createdAt) {}
    record View(long version, JsonNode research, BigDecimal weightedScore, List<Estimate> estimates) {}
    private static final int[] WEIGHTS={25,25,20,20,10};
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final AuditTrail audit;
    SelectionResearch(JdbcClient jdbc,ObjectMapper json,AuditTrail audit){this.jdbc=jdbc;this.json=json;this.audit=audit;}
    @Transactional(readOnly=true)
    View get(String id){
        return jdbc.sql("SELECT version,research_json,weighted_score FROM selection_candidate WHERE id=:id")
            .param("id",id).query((r,n)->new View(r.getLong(1),tree(r.getString(2)),r.getBigDecimal(3),estimates(id)))
            .optional().orElseThrow(()->error(404,"候选不存在"));
    }
    List<Estimate> estimates(String id){return jdbc.sql("SELECT * FROM selection_estimate WHERE candidate_id=:id ORDER BY revision DESC")
        .param("id",id).query((r,n)->new Estimate(r.getString("id"),r.getInt("revision"),tree(r.getString("payload_json")),tree(r.getString("result_json")),r.getTimestamp("created_at").toInstant().toString())).list();}
    void check(String id,Long version){
        var row=jdbc.sql("SELECT version,status FROM selection_candidate WHERE id=:id FOR UPDATE").param("id",id)
            .query((r,n)->Map.entry(r.getLong(1),r.getString(2))).optional().orElseThrow(()->error(404,"候选不存在"));
        require(version!=null && version>=0,"版本号必填");
        if(row.getKey().longValue()!=version)throw error(409,"资料已更新，请重新载入；当前输入未覆盖他人修改");
        if(Set.of("APPROVED","REJECTED").contains(row.getValue()))throw error(409,"终态评估已冻结，仅可追加记录和证据");
    }
    View save(String id,Research in,AuthenticatedActor actor){
        check(id,in.version());
        require(in.riskStatus()!=null&&Set.of("PENDING","ACCEPTABLE","BLOCKED").contains(in.riskStatus()),"请选择风险状态");
        text(in.customer(),2000);text(in.differentiation(),2000);text(in.supply(),2000);text(in.riskReason(),2000);text(in.conclusion(),2000);
        require(in.criteria()!=null&&in.criteria().size()==5,"请提供五项评分，可留空待完善");
        BigDecimal total=BigDecimal.ZERO;boolean complete=true;
        for(int k=0;k<5;k++) { var c=in.criteria().get(k);require(c!=null,"评分项不能为空");text(c.reason(),1000);
            if(c.score()==null){complete=false;continue;}
            require(c.score()>=0&&c.score()<=5,"分项评分为0至5的整数");
            require(c.reason()!=null&&!c.reason().isBlank(),"每项评分需填写依据");
            total=total.add(BigDecimal.valueOf(c.score()*WEIGHTS[k]).divide(BigDecimal.valueOf(5)));
        }
        if(in.estimateId()!=null&&!in.estimateId().isBlank())require(estimates(id).stream().anyMatch(e->e.id().equals(in.estimateId())),"试算方案不属于当前候选");
        jdbc.sql("UPDATE selection_candidate SET research_json=:research,weighted_score=:score,version=version+1 WHERE id=:id")
            .param("id",id).param("research",encode(in)).param("score",complete?total.setScale(1):null).update();
        record(actor,id,"CANDIDATE_RESEARCH_UPDATE");return get(id);
    }
    Estimate estimate(String id,EstimateInput in,AuthenticatedActor actor){
        require(in.requestId()!=null&&in.requestId().matches("[A-Za-z0-9_-]{8,100}"),"请求号无效");
        // Serialize both first writes and retries; replay compares the full original request.
        jdbc.sql("SELECT id FROM selection_candidate WHERE id=:id FOR UPDATE").param("id",id).query(String.class).optional().orElseThrow(()->error(404,"候选不存在"));
        String payload=encode(in);
        var prior=estimates(id).stream().filter(e->e.input().path("requestId").asText().equals(in.requestId())).findFirst();
        if(prior.isPresent()){if(!prior.get().input().equals(tree(payload)))throw error(409,"请求号已用于不同试算");return prior.get();}
        check(id,in.version());
        Result result=calculate(in);String eid=UUID.randomUUID().toString();int revision=estimates(id).size()+1;
        jdbc.sql("INSERT INTO selection_estimate(id,candidate_id,request_id,payload_json,result_json,revision) VALUES(:eid,:id,:request,:payload,:result,:revision)")
            .param("eid",eid).param("id",id).param("request",in.requestId()).param("payload",payload).param("result",encode(result)).param("revision",revision).update();
        jdbc.sql("UPDATE selection_candidate SET version=version+1 WHERE id=:id").param("id",id).update();
        record(actor,id,"CANDIDATE_ESTIMATE_APPEND");return estimates(id).get(0);
    }
    static Result calculate(EstimateInput in){
        require(in.name()!=null&&!in.name().isBlank(),"方案名称必填");text(in.name(),200);
        require("USD".equals(in.currency()),"首期售价币种为USD");number(in.price(),true);
        require(in.costs()!=null&&in.costs().size()==5,"采购、包装、头程、履约和其他费用均须明确录入，无需费用请明确填0");
        require(in.rateDate()!=null&&!in.rateDate().isAfter(LocalDate.now())&&in.rateDate().getYear()>=2000,"汇率日期无效");
        require(in.basis()!=null&&!in.basis().isBlank(),"费率与试算依据必填");text(in.basis(),2000);
        BigDecimal cost=BigDecimal.ZERO;
        for(Cost c:in.costs()){
            require(c!=null,"费用项必填");number(c.amount(),false);number(c.exchangeRate(),true);
            require(Set.of("USD","CNY").contains(c.currency()==null?"":c.currency()),"费用币种仅支持USD或CNY");
            require(c.name()!=null&&!c.name().isBlank(),"费用名称必填");text(c.name(),100);
            require(c.basis()!=null&&!c.basis().isBlank(),"每项费用含零费用都需依据");text(c.basis(),1000);
            if(c.currency().equals("USD"))require(c.exchangeRate().compareTo(BigDecimal.ONE)==0,"USD兑换USD汇率必须为1");
            cost=cost.add(c.amount().multiply(c.exchangeRate()));
        }
        for(BigDecimal r:Arrays.asList(in.commissionRate(),in.advertisingRate(),in.lossRate())){number(r,false);require(r.compareTo(BigDecimal.ONE)<=0,"费率应为0到1的小数");}
        BigDecimal net=BigDecimal.ONE.subtract(in.commissionRate()).subtract(in.advertisingRate()).subtract(in.lossRate());
        BigDecimal profit=in.price().multiply(net).subtract(cost);
        return new Result(cost,profit,profit.divide(in.price(),10,RoundingMode.HALF_UP),
            net.signum()>0?cost.divide(net,10,RoundingMode.HALF_UP):null,
            in.price().multiply(new BigDecimal("0.9")).multiply(net).subtract(cost),
            in.price().multiply(net).subtract(cost.multiply(new BigDecimal("1.1"))));
    }
    void ready(String id,boolean approval){
        var basic=jdbc.sql("SELECT owner,selection_reason FROM selection_candidate WHERE id=:id").param("id",id).query((r,n)->Arrays.asList(r.getString(1),r.getString(2))).single();
        require(basic.stream().allMatch(s->s!=null&&!s.isBlank()),"提交评审需要负责人和选品理由");
        require(jdbc.sql("SELECT COUNT(*) FROM selection_candidate_evidence WHERE candidate_id=:id").param("id",id).query(Long.class).single()>0,"提交评审至少需要一条证据");
        if(!approval)return;
        View view=get(id);JsonNode r=view.research();
        require(view.weightedScore()!=null,"通过前请完成五项评分与依据");
        require("ACCEPTABLE".equals(r.path("riskStatus").asText())&&!r.path("riskReason").asText().isBlank(),"请完成风险核查并解决阻断风险");
        require(!r.path("conclusion").asText().isBlank(),"审核结论必填");
        require(view.estimates().stream().anyMatch(e->e.id().equals(r.path("estimateId").asText())),"请指定本次评审的有效试算方案");
    }
    String approvalSnapshot(String id){return encode(get(id));}
    private void record(AuthenticatedActor actor,String id,String action){audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"SELECTION_CANDIDATE",id,true,null,Map.of("version",get(id).version()),"保存选品研究版本"));}
    private static void number(BigDecimal n,boolean positive){require(n!=null&&n.signum()>=(positive?1:0)&&n.compareTo(new BigDecimal("999999999"))<=0&&n.scale()<=6,"数值必填、非负且最多六位小数；售价和汇率必须大于0");}
    private static void text(String s,int max){require(s==null||s.length()<=max,"字段过长");}
    private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode tree(String s){try{return s==null?json.createObjectNode():json.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void require(boolean ok,String message){if(!ok)throw error(400,message);}
    private static SelectionException error(int status,String message){return new SelectionException(status,"SELECTION_RESEARCH",message);}
}
