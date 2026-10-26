package com.jiugeng.erp.collection;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/collection")
class CollectionController {
    private final CollectionService service;private final JdbcClient jdbc;private final TransactionTemplate tx;
    CollectionController(CollectionService service,JdbcClient jdbc,TransactionTemplate tx){this.service=service;this.jdbc=jdbc;this.tx=tx;}
    record Configuration(String kind,String targetId,String provider,boolean enabled,Long version){}
    @GetMapping("/status") @PreAuthorize("isAuthenticated()") Map<String,Object> status(){return Map.of("sources",service.sources(),"state",service.sources().stream().anyMatch(CollectionService.Source::approved)?"CONFIGURED":"WAITING_SOURCE","mode","US · America/Los_Angeles · 每日调度");}
    @GetMapping("/schedules") @PreAuthorize("hasAuthority('system.users.manage')") Object schedules(){return service.schedules();}
    @GetMapping("/jobs") @PreAuthorize("hasAuthority('system.users.manage')") Object jobs(){return service.jobs();}
    @PutMapping("/schedules") @PreAuthorize("hasAuthority('system.users.manage')") Object configure(@RequestBody Configuration c){return service.configure(c.kind(),c.targetId(),c.provider(),c.enabled(),c.version());}
    @GetMapping("/notifications") @PreAuthorize("isAuthenticated()") Object notifications(@AuthenticationPrincipal AuthenticatedActor actor){return jdbc.sql("SELECT id,kind,target_id AS targetId,message,is_read AS isRead,created_at AS createdAt FROM collection_notice WHERE user_id=:user ORDER BY created_at DESC,id LIMIT 100").param("user",actor.userId()).query().listOfRows();}
    @GetMapping("/subscription") @PreAuthorize("isAuthenticated()") Map<String,Boolean> subscription(@AuthenticationPrincipal AuthenticatedActor actor){return Map.of("enabled",jdbc.sql("SELECT enabled FROM collection_subscription WHERE user_id=:user").param("user",actor.userId()).query(Boolean.class).optional().orElse(false));}
    @PutMapping("/subscription") @PreAuthorize("isAuthenticated()") void subscribe(@RequestBody Map<String,Boolean> input,@AuthenticationPrincipal AuthenticatedActor actor){tx.executeWithoutResult(s->{jdbc.sql("SELECT id FROM collection_guard WHERE id=1 FOR UPDATE").query(Integer.class).single();int changed=jdbc.sql("UPDATE collection_subscription SET enabled=:enabled WHERE user_id=:user").param("user",actor.userId()).param("enabled",Boolean.TRUE.equals(input.get("enabled"))).update();if(changed==0)jdbc.sql("INSERT INTO collection_subscription(user_id,enabled) VALUES(:user,:enabled)").param("user",actor.userId()).param("enabled",Boolean.TRUE.equals(input.get("enabled"))).update();});}
    @PutMapping("/notifications/{id}/read") @PreAuthorize("isAuthenticated()") void read(@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor actor){jdbc.sql("UPDATE collection_notice SET is_read=TRUE WHERE id=:id AND user_id=:user").param("id",id).param("user",actor.userId()).update();}
}
@Configuration @Profile("!test & !mock") @EnableScheduling
class CollectionScheduling {
    private final CollectionService service;CollectionScheduling(CollectionService service){this.service=service;}
    @Scheduled(fixedDelayString="${erp.collection.poll-ms:60000}",initialDelay=30000) void poll(){service.tick();}
}
