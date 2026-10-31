package com.jiugeng.erp.development;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jiugeng.erp.launch.LaunchWorkspace;
import com.jiugeng.erp.iam.AuthenticatedActor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.jiugeng.erp.launch.LaunchRules.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/development/workbench") @Transactional
class DevelopmentWorkbench {
    private final LaunchWorkspace work;private final DevelopmentService service;private final JdbcClient jdbc;private final ObjectMapper json;
    DevelopmentWorkbench(LaunchWorkspace work,DevelopmentService service,JdbcClient jdbc,ObjectMapper json){this.work=work;this.service=service;this.jdbc=jdbc;this.json=json;}
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    org.springframework.http.ResponseEntity<Map<String,String>> error(org.springframework.web.server.ResponseStatusException e){return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()));}
    @GetMapping @PreAuthorize("hasAuthority('development.read')")
    Map<String,Object> list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String state,@RequestParam(defaultValue="")String owner,@RequestParam(defaultValue="-1")int stage,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){
        require(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size)&&stage>=-1&&stage<=4,"筛选或分页参数无效");
        String effective="COALESCE(w.state,CASE WHEN c.status='IN_PROGRESS' THEN 'ACTIVE' ELSE c.status END)";
        String from=" FROM product_development c LEFT JOIN launch_workspace w ON w.kind='DEVELOPMENT' AND w.entity_id=c.id WHERE (LOWER(c.name) LIKE :q OR LOWER(c.number) LIKE :q OR LOWER(COALESCE(c.developer,'')) LIKE :q) AND (:owner='' OR c.developer=:owner) AND (:stage=-1 OR COALESCE(w.stage,0)=:stage)";
        String filtered=from+" AND (:state='' OR "+effective+"=:state)";
        var parameters=Map.of("q","%"+q.trim().toLowerCase(Locale.ROOT)+"%","owner",owner,"stage",stage,"state",state,"size",size,"offset",(long)page*size);
        var items=jdbc.sql("SELECT c.id,c.name,c.number,c.source_type,c.developer,c.status AS legacy_state,"+effective+" AS state,w.stage,w.document_json,w.proposal_json,c.updated_at"+filtered+" ORDER BY c.updated_at DESC,c.id LIMIT :size OFFSET :offset").params(parameters).query((r,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getString("id"));m.put("title",r.getString("name"));m.put("number",r.getString("number"));m.put("source",r.getString("source_type"));m.put("owner",r.getString("developer"));m.put("state",r.getString("state"));m.put("legacy_state",r.getString("legacy_state"));m.put("stage",r.getInt("stage"));m.put("document",work.decode(r.getString("document_json")));m.put("hasProposal",!work.decode(r.getString("proposal_json")).isEmpty());m.put("updatedAt",r.getTimestamp("updated_at").toInstant());return m;}).list();
        long count=jdbc.sql("SELECT COUNT(*)"+filtered).params(parameters).query(Long.class).single();
        Map<String,Long> counts=new LinkedHashMap<>();jdbc.sql("SELECT "+effective+" AS state,COUNT(*) AS amount"+from+" GROUP BY "+effective).params(parameters).query((r,n)->Map.entry(r.getString("state"),r.getLong("amount"))).list().forEach(e->counts.put(e.getKey(),e.getValue()));
        return Map.of("items",items,"total",count,"counts",counts,"owners",jdbc.sql("SELECT DISTINCT developer FROM product_development WHERE developer IS NOT NULL AND developer<>'' ORDER BY developer").query(String.class).list());
    }
    record Create(String title,String owner,String reason,String requestId){}
    @PostMapping @PreAuthorize("hasAuthority('development.write')")
    Map<String,String> create(@RequestBody Create in,@AuthenticationPrincipal AuthenticatedActor actor){
        require(in.title()!=null&&!in.title().isBlank(),"名称必填");
        var fields=new DevelopmentTypes.Fields(in.title(),in.owner(),null,0,null,null,null,in.title(),null,null,null,null,null,null,null,null,null,null,null,null,in.reason(),null);
        var created=service.create(new DevelopmentTypes.Create(in.requestId(),fields),actor);initialize(created.id());return Map.of("id",created.id());
    }
    @PostMapping("/{id}/initialize") @PreAuthorize("hasAuthority('development.write')")
    LaunchWorkspace.View initialize(@PathVariable String id){var item=service.detail(id).item();jdbc.sql("SELECT id FROM product_development WHERE id=:id FOR UPDATE").param("id",id).query(String.class).single();
        ObjectNode d=json.createObjectNode();d.put("name",item.fields().name());d.put("owner",item.fields().developer());d.put("buyer",item.fields().buyer());d.put("reason",item.fields().remark());d.put("market","US");d.put("specVersion","1");d.put("productId",item.fields().linkedProductId());d.put("legacyNotes",item.fields().sampleNotes());if(item.fields().startDate()!=null)d.put("startDate",item.fields().startDate().toString());if(item.fields().endDate()!=null)d.put("endDate",item.fields().endDate().toString());
        if(item.source()!=null){d.put("sourceCandidateId",item.source().candidateId());d.put("asin",item.source().asin());}
        String state=switch(item.status()){case DRAFT->"DRAFT";case IN_PROGRESS->"ACTIVE";case COMPLETED->"COMPLETED";case VOID->"VOID";};
        work.create("DEVELOPMENT",id,state,d,item.source()!=null||item.status()!=DevelopmentTypes.Status.DRAFT?d:null);return work.get("DEVELOPMENT",id);}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('development.read')")
    Map<String,Object> get(@PathVariable String id){return Map.of("legacy",service.detail(id),"workspace",work.exists("DEVELOPMENT",id)?work.get("DEVELOPMENT",id):Map.of());}
    @GetMapping("/{id}/readiness") @PreAuthorize("hasAuthority('development.read')")
    Map<String,Object> readiness(@PathVariable String id){service.detail(id);return work.developmentReadiness(id);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('development.write')")
    LaunchWorkspace.View save(@PathVariable String id,@RequestBody LaunchWorkspace.Save input,@AuthenticationPrincipal AuthenticatedActor actor){var result=work.save("DEVELOPMENT",id,input,actor);jdbc.sql("UPDATE product_development SET name=:name,developer=:owner,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("name",text(result.document(),"name")).param("owner",text(result.document(),"owner")).param("id",id).update();return result;}
    @PostMapping("/{id}/action") @PreAuthorize("hasAuthority('development.write')")
    LaunchWorkspace.View action(@PathVariable String id,@RequestBody LaunchWorkspace.Command c,@AuthenticationPrincipal AuthenticatedActor actor,Authentication auth){var result=work.act("DEVELOPMENT",id,c,auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("development.review")),actor);
        String status=switch(result.state()){case "COMPLETED"->"COMPLETED";case "VOID"->"VOID";case "DRAFT","PROJECT_REVIEW"->"DRAFT";default->"IN_PROGRESS";};
        jdbc.sql("UPDATE product_development SET status=:s,developer=:owner,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("s",status).param("owner",text(result.document(),"owner")).param("id",id).update();return result;}
}
