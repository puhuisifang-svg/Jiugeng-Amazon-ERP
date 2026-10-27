package com.jiugeng.erp.watch;

import com.jiugeng.erp.collection.CollectionService;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/watch/events")
class WatchNoveltyController {
    private final JdbcClient jdbc;private final WatchRepository repository;private final CollectionService collection;private final AuditTrail audit;
    WatchNoveltyController(JdbcClient jdbc,WatchRepository repository,CollectionService collection,AuditTrail audit){this.jdbc=jdbc;this.repository=repository;this.collection=collection;this.audit=audit;}
    // Only discovery events need detail reads; first baseline products never enter this queue.
    @Scheduled(fixedDelayString="${erp.collection.poll-ms:5000}",initialDelay=35000)
    void discover(){
        var ids=jdbc.sql("SELECT e.id FROM watch_new_product_event e JOIN watch_run r ON r.id=e.detected_run_id JOIN watch_store s ON s.id=e.store_id WHERE r.adapter_name='amazon-public-us' AND s.removed=FALSE AND s.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM collection_schedule c WHERE c.kind='WATCH_NOVELTY' AND c.target_id=e.id) ORDER BY e.first_detected_at DESC LIMIT 20").query(String.class).list();
        for(String id:ids){try{collection.enqueue("WATCH_NOVELTY",id);}catch(org.springframework.web.server.ResponseStatusException ignored){/* A concurrent pause/removal fences the queue. */}}
    }
    @PostMapping("/{id}/novelty/collect") @PreAuthorize("hasAuthority('watch.write')")
    void retry(@PathVariable String id){collection.enqueue("WATCH_NOVELTY",id);}
    record Mark(Long version,String action){}
    @PutMapping("/{id}/novelty") @PreAuthorize("hasAuthority('watch.write')") @Transactional
    WatchTypes.EventView mark(@PathVariable String id,@RequestBody Mark input,@AuthenticationPrincipal AuthenticatedActor actor){
        if(input==null||input.version()==null||input.action()==null||!Set.of("CONFIRMED","EXCLUDED","RESET").contains(input.action()))throw new WatchException(400,"WATCH_NOVELTY_INPUT","请选择确认新品、排除或撤销人工标记，并提供版本号");
        repository.lockEvent(id);var old=repository.event(id).orElseThrow();
        if(input.version()!=old.noveltyVersion())throw new WatchException(409,"WATCH_NOVELTY_VERSION","人工标记已更新，请刷新后重试");
        jdbc.sql("UPDATE watch_new_product_event SET novelty_override=:mark,novelty_version=novelty_version+1 WHERE id=:id").param("mark",input.action().equals("RESET")?null:input.action()).param("id",id).update();
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"WATCH_NOVELTY_MARK","WATCH",id,true,Map.of("mark",Objects.toString(old.noveltyOverride(),"AUTO")),Map.of("mark",input.action()),"新品人工标记"));
        return repository.event(id).orElseThrow();
    }
}
