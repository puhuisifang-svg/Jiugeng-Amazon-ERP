package com.jiugeng.erp.watch;

import com.jiugeng.erp.collection.CollectionAdapter;
import com.jiugeng.erp.collection.CollectionTarget;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Enriches existing discovery events; never creates events or changes their discovery date. */
@Component @Profile("!mock")
class WatchNoveltyTarget implements CollectionTarget {
    static final int WINDOW_DAYS=60;
    private final JdbcClient jdbc;private final WatchRepository repository;
    WatchNoveltyTarget(JdbcClient jdbc,WatchRepository repository){this.jdbc=jdbc;this.repository=repository;}
    public String kind(){return "WATCH_NOVELTY";}
    public CollectionAdapter.Target prepare(String id){
        var event=repository.event(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"上架记录不存在"));
        repository.lockStore(event.storeId());var store=repository.store(event.storeId()).orElseThrow();
        boolean eligible=jdbc.sql("SELECT COUNT(*) FROM watch_store s JOIN watch_run r ON r.store_id=s.id WHERE s.id=:store AND s.removed=FALSE AND r.id=:run AND r.adapter_name='amazon-public-us'").param("store",store.id()).param("run",event.sourceRunId()).query(Long.class).single()>0;
        if(!eligible||!store.status().equals("ACTIVE"))throw new ResponseStatusException(HttpStatus.CONFLICT,"仅对监控中的公开网页上架记录补采日期");
        return new CollectionAdapter.Target(kind(),id,event.asin(),store.version());
    }
    record Assessment(String status,LocalDate date,String reason){}
    static Assessment assess(String raw,Instant detected){
        LocalDate date=null;
        if(raw!=null){
            String clean=raw.replaceAll("[\\p{Cf}]","").trim();
            for(DateTimeFormatter format:List.of(DateTimeFormatter.ISO_LOCAL_DATE,DateTimeFormatter.ofPattern("MMMM d, uuuu",Locale.US).withResolverStyle(ResolverStyle.STRICT),DateTimeFormatter.ofPattern("MMM d, uuuu",Locale.US).withResolverStyle(ResolverStyle.STRICT))){try{date=LocalDate.parse(clean,format);break;}catch(DateTimeParseException ignored){}}
        }
        if(date==null)return new Assessment("PENDING",null,raw==null||raw.isBlank()?"页面未提供首次可用日期，无法确认新品":"页面日期无法识别，待核实");
        long age=ChronoUnit.DAYS.between(date,detected.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate());
        if(age<0)return new Assessment("PENDING",date,"页面日期晚于首次发现日期，待核实");
        if(age<=WINDOW_DAYS)return new Assessment("CANDIDATE",date,"首次发现时，亚马逊首次可用日期距发现日 "+age+" 天（统一窗口 60 天）");
        return new Assessment("OLD",date,"首次发现时，亚马逊首次可用日期距发现日 "+age+" 天，超过 60 天窗口");
    }
    public String apply(CollectionAdapter.Target target,String provider,String job,CollectionAdapter.Observation observation){
        var event=repository.event(target.id()).orElseThrow();
        var fields=observation.fields();
        String raw=fields==null?null:fields.path("listedAt").asText(null);
        Assessment result=assess(raw,event.detectedAt());
        if(result.date()==null&&event.availableDate()!=null)result=assess(event.availableDate(),event.detectedAt());
        // Automatic evidence is independent of versioned manual overrides.
        jdbc.sql("UPDATE watch_new_product_event SET novelty_status=:status,available_date=:date,novelty_reason=:reason,novelty_checked_at=:at WHERE id=:id")
            .param("status",result.status()).param("date",result.date()==null?null:java.sql.Date.valueOf(result.date())).param("reason",result.reason()).param("at",Timestamp.from(observation.observedAt())).param("id",target.id()).update();
        jdbc.sql("UPDATE collection_schedule SET enabled=FALSE,manual_requested=FALSE WHERE kind='WATCH_NOVELTY' AND target_id=:id").param("id",target.id()).update();
        return "";
    }
}
