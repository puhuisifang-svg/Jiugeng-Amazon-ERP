package com.jiugeng.erp.selection;

import static com.jiugeng.erp.selection.SelectionTypes.*;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SelectionService implements SelectionCandidateGateway {
    private static final Pattern ASIN = Pattern.compile("[A-Z0-9]{10,16}");
    private static final Pattern PATH = Pattern.compile("/(?:DP|GP/PRODUCT)/([A-Z0-9]{10,16})(?:/|$)");
    private final SelectionRepository repository;
    private final AuditTrail audit;
    private final SelectionResearch research;
    private final org.springframework.context.ApplicationEventPublisher events;
    SelectionService(SelectionRepository repository, AuditTrail audit, org.springframework.context.ApplicationEventPublisher events, SelectionResearch research) {
        this.repository=repository; this.audit=audit; this.events=events; this.research=research;
    }

    @Transactional(readOnly=true)
    List<CandidateView> candidates(String keyword, CandidateStatus status, String owner) {
        return repository.findAll(blank(keyword), status, blank(owner));
    }
    @Transactional(readOnly=true)
    DetailView detail(String id) { return new DetailView(candidate(id), repository.notes(id), repository.evidence(id)); }

    @Transactional
    CandidateView create(String asinOrUrl, String title, Integer score, AuthenticatedActor actor) {
        String asin=extractAsin(required(asinOrUrl,"ASIN 或 URL",1000));
        String cleanTitle=required(title,"标题",300);
        int cleanScore=score==null?50:score;
        if(cleanScore<0||cleanScore>100) throw invalid("CANDIDATE_SCORE_INVALID","综合分必须在 0 到 100 之间");
        if(repository.asinExists(asin)) throw conflict("CANDIDATE_ASIN_EXISTS","该 ASIN 已存在候选："+asin);
        String id=UUID.randomUUID().toString();
        try { repository.insert(id,asin,cleanTitle,cleanScore,canonical(asin),actor.userId()); }
        catch(DataIntegrityViolationException e){ throw conflict("CANDIDATE_ASIN_EXISTS","该 ASIN 已存在候选："+asin); }
        CandidateView created=candidate(id);
        audit.record(operation(actor,"CANDIDATE_CREATE",id,null,snapshot(created),"创建候选"));
        return created;
    }

    @Transactional
    CandidateView evaluate(String id, EvaluationInput input, Long version, AuthenticatedActor actor) {
        repository.lock(id);
        research.check(id,version);
        CandidateView before=candidate(id);
        String category=optional(input.category(),100), owner=optional(input.owner(),100), reason=optional(input.selectionReason(),1000);
        if(input.price()!=null&&input.price().signum()<0) throw invalid("CANDIDATE_PRICE_INVALID","价格不能小于 0");
        if(input.rating()!=null&&(input.rating().compareTo(BigDecimal.ZERO)<0||input.rating().compareTo(new BigDecimal("5"))>0)) throw invalid("CANDIDATE_RATING_INVALID","星级必须在 0 到 5 之间");
        if(input.reviewCount()!=null&&input.reviewCount()<0) throw invalid("CANDIDATE_REVIEW_COUNT_INVALID","评论数不能小于 0");
        Set<String> seen=new LinkedHashSet<>();
        List<String> tags=(input.tags()==null?List.<String>of():input.tags()).stream().map(String::trim).filter(v->!v.isBlank())
                .filter(v->{ if(v.length()>64) throw invalid("CANDIDATE_TAG_INVALID","标签长度不能超过 64"); return seen.add(v.toUpperCase(Locale.ROOT)); }).toList();
        if(tags.size()>20) throw invalid("CANDIDATE_TAG_INVALID","标签不能超过 20 个");
        EvaluationInput clean=new EvaluationInput(category,input.price(),input.rating(),input.reviewCount(),owner,tags,reason);
        repository.updateEvaluation(id,clean,actor.userId());
        CandidateView after=candidate(id);
        audit.record(operation(actor,"CANDIDATE_EVALUATION_UPDATE",id,snapshot(before),snapshot(after),"更新候选评估"));
        return after;
    }

    @Transactional
    CandidateView transition(String id, CandidateStatus target, String rejectionReason, Long version, AuthenticatedActor actor) {
        repository.lock(id);
        research.check(id,version);
        CandidateView before=candidate(id);
        if(target==null||!allowed(before.status()).contains(target)) throw conflict("INVALID_CANDIDATE_TRANSITION","不允许从 "+before.status()+" 变更为 "+target);
        String reason=optional(rejectionReason,500);
        if(target==CandidateStatus.REJECTED&&reason==null) throw invalid("REJECTION_REASON_REQUIRED","淘汰候选必须填写原因");
        if(target!=CandidateStatus.REJECTED&&reason!=null) throw invalid("REJECTION_REASON_NOT_ALLOWED","仅淘汰状态可以填写原因");
        if(target==CandidateStatus.REVIEWING||target==CandidateStatus.APPROVED) research.ready(id,target==CandidateStatus.APPROVED);
        repository.transition(id,target,reason,actor.userId());
        CandidateView after=candidate(id);
        audit.record(operation(actor,"CANDIDATE_TRANSITION",id,snapshot(before),snapshot(after),"候选状态流转"));
        if (target == CandidateStatus.APPROVED) events.publishEvent(new CandidateApproved(after.id(), after.asin(),
                after.title(), after.category(), after.score(), after.price(), after.sourceUrl(),
                after.selectionReason(), after.owner(), actor, research.approvalSnapshot(id)));
        return after;
    }

    @Transactional
    DetailView addNote(String id,String content,String role,AuthenticatedActor actor){
        candidate(id); String clean=required(content,"评审记录",1000); String noteId=UUID.randomUUID().toString();
        repository.insertNote(noteId,id,clean,actor.userId(),actor.displayName(),role);
        audit.record(operation(actor,"CANDIDATE_NOTE_APPEND",id,null,Map.of("noteId",noteId,"contentLength",clean.length()),"追加评审记录（正文未写入审计快照）"));
        return detail(id);
    }

    @Transactional
    DetailView addEvidence(String id,String name,String url,String description,String role,AuthenticatedActor actor){
        candidate(id); String cleanName=required(name,"证据名称",200), cleanUrl=required(url,"证据链接",1000), cleanDescription=optional(description,1000);
        validateHttps(cleanUrl); String evidenceId=UUID.randomUUID().toString();
        repository.insertEvidence(evidenceId,id,cleanName,cleanUrl,cleanDescription,actor.userId(),actor.displayName(),role);
        audit.record(operation(actor,"CANDIDATE_EVIDENCE_APPEND",id,null,Map.of("evidenceId",evidenceId,"name",cleanName,"url","[REDACTED_HTTPS_URL]"),"追加证据（URL 已脱敏）"));
        return detail(id);
    }

    @Override @Transactional
    public Confirmation confirmWatchCandidate(String asin,String title,String storeName,java.time.Instant discoveredAt,
            String runId,String eventId,AuthenticatedActor actor){
        String normalized=extractAsin(asin);
        var existing=repository.findByAsin(normalized);
        if(existing.isPresent()) { repository.linkSource(existing.get().id(),eventId,runId); return new Confirmation(existing.get().id(),true); }
        String id=UUID.randomUUID().toString();
        try { repository.insertWatch(id,normalized,required(title,"标题",300),canonical(normalized),
                required(storeName,"店铺名称",200),discoveredAt,required(runId,"来源运行",100),required(eventId,"来源事件",100),actor.userId()); }
        catch(DataIntegrityViolationException conflict){
            String found=repository.findByAsin(normalized).orElseThrow(()->conflict("CANDIDATE_ASIN_EXISTS","ASIN 并发确认冲突")).id(); repository.linkSource(found,eventId,runId); return new Confirmation(found,true);
        }
        CandidateView created=candidate(id);
        audit.record(operation(actor,"CANDIDATE_CREATE_FROM_WATCH",id,null,snapshot(created),"确认监控事件生成候选"));
        repository.linkSource(id,eventId,runId); return new Confirmation(id,false);
    }

    private CandidateView candidate(String id){ return repository.findById(id).orElseThrow(()->new SelectionException(404,"CANDIDATE_NOT_FOUND","候选不存在："+id)); }
    private String extractAsin(String input){
        String value=input.trim(), upper=value.toUpperCase(Locale.ROOT);
        if(ASIN.matcher(upper).matches()) return upper;
        try { URI uri=URI.create(value); String host=uri.getHost();
            if(host==null||!(host.equalsIgnoreCase("amazon.com")||host.toLowerCase(Locale.ROOT).endsWith(".amazon.com"))) throw new IllegalArgumentException();
            Matcher matcher=PATH.matcher(uri.getPath().toUpperCase(Locale.ROOT)+"/"); if(matcher.find()) return matcher.group(1);
        } catch(IllegalArgumentException ignored){}
        throw invalid("INVALID_ASIN_OR_URL","请输入 ASIN 或标准 amazon.com 商品链接");
    }
    private void validateHttps(String value){ try { URI uri=URI.create(value); if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().isBlank()||uri.getUserInfo()!=null) throw new IllegalArgumentException(); }catch(IllegalArgumentException e){ throw invalid("CANDIDATE_EVIDENCE_HTTPS_URL_REQUIRED","证据链接必须是有效 HTTPS URL"); } }
    private Set<CandidateStatus> allowed(CandidateStatus status){ return switch(status){case NEW->Set.of(CandidateStatus.REVIEWING,CandidateStatus.REJECTED);case REVIEWING->Set.of(CandidateStatus.APPROVED,CandidateStatus.REJECTED);case APPROVED,REJECTED->Set.of();}; }
    private String required(String value,String label,int max){String clean=blank(value);if(clean==null||clean.length()>max)throw invalid("CANDIDATE_FIELD_INVALID",label+"不能为空且长度不能超过 "+max);return clean;}
    private String optional(String value,int max){String clean=blank(value);if(clean!=null&&clean.length()>max)throw invalid("CANDIDATE_FIELD_INVALID","字段长度不能超过 "+max);return clean;}
    private String blank(String value){if(value==null)return null;String clean=value.trim();return clean.isEmpty()?null:clean;}
    private String canonical(String asin){return "https://www.amazon.com/dp/"+asin;}
    private SelectionException invalid(String code,String message){return new SelectionException(400,code,message);}
    private SelectionException conflict(String code,String message){return new SelectionException(409,code,message);}
    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor,String action,String id,Map<String,?> before,Map<String,?> after,String detail){return new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"SELECTION_CANDIDATE",id,true,before,after,detail);}
    private Map<String,Object> snapshot(CandidateView c){Map<String,Object> value=new LinkedHashMap<>();value.put("id",c.id());value.put("asin",c.asin());value.put("status",c.status());value.put("score",c.score());value.put("owner",c.owner());value.put("tags",c.tags());value.put("source",c.source());return value;}
}
