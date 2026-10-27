package com.jiugeng.erp.watch;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/watch")
@Transactional
class WatchWorkbench {
    private final JdbcClient jdbc;private final WatchRepository repository;private final AuditTrail audit;
    WatchWorkbench(JdbcClient jdbc,WatchRepository repository,AuditTrail audit){this.jdbc=jdbc;this.repository=repository;this.audit=audit;}
    record StoreEdit(Long version,String name,String owner,String remark,boolean enabled){}
    record EventEdit(Long version,boolean ignored,String reason){}
    record Product(String asin,String firstDetectedAt,boolean online,String source){}
    record Detail(WatchTypes.StoreView store,List<WatchTypes.RunView> runs,List<Product> products,List<Map<String,Object>> automaticProducts){}
    @GetMapping("/stores/{id}") @PreAuthorize("hasAuthority('watch.read')")
    Detail detail(@PathVariable String id){
        var store=repository.store(id).orElseThrow(()->error(404,"店铺不存在"));
        var runIds=jdbc.sql("SELECT id FROM watch_run WHERE store_id=:id ORDER BY completed_at DESC,id DESC LIMIT 100").param("id",id).query(String.class).list();
        var active=repository.baseline(id);
        var products=jdbc.sql("SELECT a.asin,MIN(r.completed_at) AS first_at FROM watch_snapshot_asin a JOIN watch_run r ON r.id=a.run_id WHERE r.store_id=:id AND r.decision='PROMOTED' GROUP BY a.asin ORDER BY first_at DESC,a.asin")
            .param("id",id).query((r,n)->new Product(r.getString("asin"),r.getTimestamp("first_at").toInstant().toString(),active.contains(r.getString("asin")),"MANUAL")).list();
        var automatic=jdbc.sql("SELECT provider,asin,title,image_url AS imageUrl,price,price_type AS priceType,rating,first_at AS firstAt,last_at AS lastAt,missing_count AS missingCount,missing_at AS missingAt FROM watch_presence WHERE store_id=:id ORDER BY first_at DESC,asin").param("id",id).query().listOfRows();
        return new Detail(store,runIds.stream().map(repository::run).toList(),products,automatic);
    }
    @PutMapping("/stores/{id}") @PreAuthorize("hasAuthority('watch.write')")
    WatchTypes.StoreView edit(@PathVariable String id,@RequestBody StoreEdit in,@AuthenticationPrincipal AuthenticatedActor actor){
        repository.lockStore(id);var store=repository.store(id).orElseThrow();version(in.version(),store.version());
        if(jdbc.sql("SELECT removed FROM watch_store WHERE id=:id").param("id",id).query(Boolean.class).single())throw error(409,"店铺已移除，请重新添加");
        if(in.name()==null||in.name().isBlank()||in.name().length()>200||in.owner()!=null&&in.owner().length()>100||in.remark()!=null&&in.remark().length()>1000)throw error(400,"名称必填，名称/监控人/备注长度超限");
        jdbc.sql("UPDATE watch_store SET store_name=:name,owner_name=:owner,remark=:remark,status=:status,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
            .param("id",id).param("name",in.name().trim()).param("owner",in.owner()).param("remark",in.remark()).param("status",in.enabled()?"ACTIVE":"DISABLED").update();
        record(actor,"WATCH_STORE_UPDATE",id);return repository.store(id).orElseThrow();
    }
    record RemarkEdit(Long version,String remark){}
    @PatchMapping("/stores/{id}/remark") @PreAuthorize("hasAuthority('watch.write')")
    WatchTypes.StoreView remark(@PathVariable String id,@RequestBody RemarkEdit in,@AuthenticationPrincipal AuthenticatedActor actor){
        repository.lockStore(id);var store=repository.store(id).orElseThrow();version(in.version(),store.version());
        if(in.remark()==null||in.remark().length()>1000)throw error(400,"备注不能超过1000字");
        jdbc.sql("UPDATE watch_store SET remark=:remark,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND removed=FALSE")
            .param("remark",in.remark()).param("id",id).update();
        record(actor,"WATCH_STORE_REMARK",id);return repository.store(id).orElseThrow();
    }
    @DeleteMapping("/stores/{id}") @PreAuthorize("hasAuthority('watch.write')")
    void remove(@PathVariable String id,@RequestParam Long version,@AuthenticationPrincipal AuthenticatedActor actor){
        repository.lockStore(id);var store=repository.store(id).orElseThrow();version(version,store.version());
        jdbc.sql("UPDATE watch_store SET removed=TRUE,status='DISABLED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
            .param("id",id).update();
        record(actor,"WATCH_STORE_REMOVE",id);
    }
    @PutMapping("/events/{id}/handling") @PreAuthorize("hasAuthority('watch.write')")
    WatchTypes.EventView handling(@PathVariable String id,@RequestBody EventEdit in,@AuthenticationPrincipal AuthenticatedActor actor){
        repository.lockEvent(id);var event=repository.event(id).orElseThrow();version(in.version(),event.version());
        if(event.candidateId()!=null)throw error(409,"已转候选的来源关系不可解除");
        if(in.ignored()&&(in.reason()==null||in.reason().isBlank()||in.reason().length()>1000))throw error(400,"忽略原因必填且不超过1000字");
        jdbc.sql("UPDATE watch_new_product_event SET ignored=:ignored,ignore_reason=:reason,version=version+1 WHERE id=:id").param("id",id).param("ignored",in.ignored()).param("reason",in.ignored()?in.reason().trim():null).update();
        record(actor,in.ignored()?"WATCH_EVENT_IGNORE":"WATCH_EVENT_RESTORE",id);return repository.event(id).orElseThrow();
    }
    private void version(Long expected,long current){if(expected==null)throw error(400,"版本号必填");if(expected!=current)throw error(409,"记录已更新，请刷新后重试");}
    private WatchException error(int status,String message){return new WatchException(status,"WATCH_WORKBENCH",message);}
    private void record(AuthenticatedActor actor,String action,String id){audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"WATCH",id,true,null,null,"监控工作台更新"));}
}
