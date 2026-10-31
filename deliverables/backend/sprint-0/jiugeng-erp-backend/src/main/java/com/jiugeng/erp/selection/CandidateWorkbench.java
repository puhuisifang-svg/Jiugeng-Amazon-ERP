package com.jiugeng.erp.selection;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.jiugeng.erp.launch.LaunchWorkspace;
import com.jiugeng.erp.iam.AuthenticatedActor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.jiugeng.erp.launch.LaunchRules.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/selection/workbench") @Transactional
class CandidateWorkbench {
    private final com.jiugeng.erp.collection.CollectionService collection;private final LaunchWorkspace work;private final SelectionService service;private final JdbcClient jdbc;private final ObjectMapper json;private final ApplicationEventPublisher events;
    CandidateWorkbench(LaunchWorkspace work,SelectionService service,JdbcClient jdbc,ObjectMapper json,ApplicationEventPublisher events,com.jiugeng.erp.collection.CollectionService collection){this.collection=collection;this.work=work;this.service=service;this.jdbc=jdbc;this.json=json;this.events=events;}
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    org.springframework.http.ResponseEntity<Map<String,String>> error(org.springframework.web.server.ResponseStatusException e){return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()));}
    @GetMapping @PreAuthorize("hasAuthority('selection.read')")
    Map<String,Object> list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String state,
            @RequestParam(defaultValue="")String source,@RequestParam(defaultValue="")String owner,
            @RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页参数无效");
        require(Set.of("","AMAZON","WATCH","OTHER").contains(source),"来源筛选无效");
        String effectiveState="COALESCE(w.state,CASE WHEN c.status='REVIEWING' THEN 'EVALUATING' ELSE c.status END)";
        String from=" FROM selection_candidate c LEFT JOIN launch_workspace w ON w.kind='CANDIDATE' AND w.entity_id=c.id LEFT JOIN launch_observation o ON o.entity_id=c.id WHERE (LOWER(c.title) LIKE :q OR LOWER(COALESCE(c.asin,'')) LIKE :q OR LOWER(COALESCE(c.owner,'')) LIKE :q OR c.id LIKE :q) AND (:owner='' OR c.owner=:owner) AND (:source='' OR CASE WHEN c.source='WATCH' THEN 'WATCH' WHEN c.asin IS NULL THEN 'OTHER' ELSE 'AMAZON' END=:source)";
        Map<String,Object> parameters=new HashMap<>(Map.of("q","%"+q.trim().toLowerCase(Locale.ROOT)+"%","state",state,"source",source,"owner",owner,"size",size,"offset",(long)page*size));
        String filtered=from+" AND (:state='' OR "+effectiveState+"=:state)";
        var items=jdbc.sql("SELECT c.id,c.title,c.asin,c.source,c.owner,c.status AS legacy_state,"+effectiveState+" AS state,w.document_json,o.observation_json,c.updated_at"+filtered+" ORDER BY c.updated_at DESC,c.id LIMIT :size OFFSET :offset").params(parameters).query((r,n)->{
            Map<String,Object> m=new LinkedHashMap<>();for(String f:List.of("id","title","asin","source","owner","state","legacy_state"))m.put(f,r.getString(f));
            m.put("document",work.decode(r.getString("document_json")));m.put("observation",work.decode(r.getString("observation_json")));m.put("updatedAt",r.getTimestamp("updated_at").toInstant());return m;}).list();
        long count=jdbc.sql("SELECT COUNT(*)"+filtered).params(parameters).query(Long.class).single();
        Map<String,Long> counts=new LinkedHashMap<>();jdbc.sql("SELECT "+effectiveState+" AS state,COUNT(*) AS amount"+from+" GROUP BY "+effectiveState).params(parameters).query((r,n)->Map.entry(r.getString("state"),r.getLong("amount"))).list().forEach(e->counts.put(e.getKey(),e.getValue()));
        var owners=jdbc.sql("SELECT DISTINCT owner FROM selection_candidate WHERE owner IS NOT NULL AND owner<>'' ORDER BY owner").query(String.class).list();
        return Map.of("items",items,"total",count,"counts",counts,"owners",owners);
    }
    record Create(String type,String asinOrUrl,String title,String owner,String reason,String sourceUrl,String requestId){}
    @PostMapping @PreAuthorize("hasAuthority('selection.write')")
    Map<String,String> create(@RequestBody Create in,@AuthenticationPrincipal AuthenticatedActor actor){
        require(!empty(in.title())&&!empty(in.owner())&&!empty(in.reason()),"名称、负责人和候选理由必填");
        require(in.title().length()<=300&&in.owner().length()<=100&&in.reason().length()<=1000,"字段过长");
        String id;
        if("OTHER".equals(in.type())){
            require(in.requestId()!=null&&in.requestId().matches("[a-fA-F0-9-]{36}"),"创建请求号必填");id=in.requestId();
            var existing=jdbc.sql("SELECT created_by,title FROM selection_candidate WHERE id=:id FOR UPDATE").param("id",id).query((r,n)->List.of(r.getString(1),r.getString(2))).optional();
            if(existing.isPresent()){require(existing.get().equals(List.of(actor.userId(),in.title())),"重复请求内容不同");return Map.of("id",id);}
            String url=empty(in.sourceUrl())?"":in.sourceUrl();require(url.isEmpty()||url.startsWith("https://"),"来源链接须为HTTPS");
            jdbc.sql("INSERT INTO selection_candidate(id,title,status,score,source_url,source,owner,selection_reason,created_by,updated_by) VALUES(:id,:title,'NEW',0,:url,'MANUAL',:owner,:reason,:actor,:actor)").param("id",id).param("title",in.title()).param("url",url).param("owner",in.owner()).param("reason",in.reason()).param("actor",actor.userId()).update();
        }else{
            try{id=service.create(in.asinOrUrl(),in.title(),0,actor).id();}
            catch(SelectionException e){if(!e.getMessage().contains("已存在"))throw e;String value=in.asinOrUrl().toUpperCase(Locale.ROOT);var found=service.candidates(null,null,null).stream().filter(c->c.asin()!=null&&value.contains(c.asin())).findFirst();if(found.isEmpty())throw e;return Map.of("id",found.get().id());}
            jdbc.sql("UPDATE selection_candidate SET owner=:owner,selection_reason=:reason WHERE id=:id").param("owner",in.owner()).param("reason",in.reason()).param("id",id).update();
        }
        initialize(id);var createdWorkspace=work.get("CANDIDATE",id);var createdDoc=(ObjectNode)createdWorkspace.document().deepCopy();createdDoc.put("owner",in.owner()).put("reason",in.reason());work.save("CANDIDATE",id,new LaunchWorkspace.Save(createdWorkspace.version(),createdDoc),actor);if(!"OTHER".equals(in.type()))collection.enqueue("CANDIDATE",id);return Map.of("id",id);
    }
    @PostMapping("/{id}/initialize") @PreAuthorize("hasAuthority('selection.write')")
    LaunchWorkspace.View initialize(@PathVariable String id){
        var c=service.detail(id).candidate();ObjectNode d=json.createObjectNode();d.put("name",c.title());d.put("owner",c.owner());d.put("reason",c.selectionReason());d.put("market","US");d.put("asin",c.asin());d.put("sourceUrl",c.sourceUrl());d.put("platform",c.asin()==null?"其他产品":"Amazon");d.put("specVersion","1");
        var r=jdbc.sql("SELECT COALESCE(research_json,'{}') FROM selection_candidate WHERE id=:id FOR UPDATE").param("id",id).query(String.class).single();JsonNode old=work.decode(r);for(String f:List.of("customer","conclusion","criteria"))if(old.has(f))d.set(f,old.get(f));
        String state=switch(c.status()){case NEW->"NEW";case REVIEWING->"EVALUATING";case APPROVED->"APPROVED";case REJECTED->"REJECTED";};
        work.create("CANDIDATE",id,state,d,c.status()==SelectionTypes.CandidateStatus.APPROVED?d:null);return work.get("CANDIDATE",id);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('selection.read')")
    Map<String,Object> get(@PathVariable String id){return Map.of("legacy",service.detail(id),"workspace",work.exists("CANDIDATE",id)?work.get("CANDIDATE",id):Map.of());}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('selection.write')")
    LaunchWorkspace.View save(@PathVariable String id,@RequestBody LaunchWorkspace.Save input,@AuthenticationPrincipal AuthenticatedActor actor){var result=work.save("CANDIDATE",id,input,actor);jdbc.sql("UPDATE selection_candidate SET title=:name,owner=:o,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("name",text(result.document(),"name")).param("o",text(result.document(),"owner")).param("id",id).update();return result;}
    @PostMapping("/{id}/action") @PreAuthorize("hasAuthority('selection.write')")
    LaunchWorkspace.View action(@PathVariable String id,@RequestBody LaunchWorkspace.Command c,@AuthenticationPrincipal AuthenticatedActor actor,Authentication auth){
        var result=work.act("CANDIDATE",id,c,auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("selection.review")),actor);
        String status=switch(result.state()){case "APPROVED"->"APPROVED";case "REJECTED"->"REJECTED";case "NEW"->"NEW";default->"REVIEWING";};
        jdbc.sql("UPDATE selection_candidate SET status=:s,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("s",status).param("id",id).update();
        if(result.state().equals("APPROVED")){
            var item=service.detail(id).candidate();events.publishEvent(new CandidateApproved(id,item.asin(),item.title(),item.category(),item.score(),item.price(),item.sourceUrl(),text(result.document(),"reason"),text(result.document(),"owner"),actor,work.encode(Map.of("workspace",result.document()))));
        }return result;
    }
    @PostMapping("/{id}/collect") @PreAuthorize("hasAuthority('selection.write')")
    Map<String,String> collect(@PathVariable String id){service.detail(id);collection.enqueue("CANDIDATE",id);return Map.of("status","QUEUED");}
    @GetMapping("/{id}/observation") @PreAuthorize("hasAuthority('selection.read')")
    Map<String,Object> observation(@PathVariable String id){service.detail(id);Map<String,Object> result=new LinkedHashMap<>();
      result.put("observation",jdbc.sql("SELECT observation_json FROM launch_observation WHERE entity_id=:id").param("id",id).query(String.class).optional().map(work::decode).orElse(work.blank()));
      result.put("job",jdbc.sql("SELECT j.status,j.reason,j.created_at FROM collection_job j JOIN collection_schedule s ON s.id=j.schedule_id WHERE s.kind='CANDIDATE' AND s.target_id=:id ORDER BY j.created_at DESC LIMIT 1").param("id",id).query().listOfRows());return result;}
    private boolean empty(String v){return v==null||v.isBlank();}
}
