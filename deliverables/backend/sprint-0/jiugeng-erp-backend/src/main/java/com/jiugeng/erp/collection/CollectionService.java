package com.jiugeng.erp.collection;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service @Profile("!mock")
public class CollectionService {
    record Schedule(String id,String kind,String targetId,String provider,boolean enabled,long version,String nextAt,String sourceStatus) {}
    record Job(String id,String scheduleId,long scheduleVersion,long targetVersion,String status,int attempts,String reason) {}
    record Claim(Job job,Schedule schedule,CollectionAdapter.Target target,String lease) {}
    record Source(String provider,Set<String> kinds,boolean approved) {}
    private final JdbcClient jdbc;private final TransactionTemplate tx;private final List<CollectionAdapter> adapters;private final List<CollectionTarget> targets;
    CollectionService(JdbcClient jdbc,TransactionTemplate tx,List<CollectionAdapter> adapters,List<CollectionTarget> targets){this.jdbc=jdbc;this.tx=tx;this.adapters=adapters;this.targets=targets;}
    private void lock(){jdbc.sql("SELECT id FROM collection_guard WHERE id=1 FOR UPDATE").query(Integer.class).single();}
    List<Source> sources(){return adapters.stream().map(a->new Source(a.provider(),a.kinds(),a.approved())).toList();}
    List<Schedule> schedules(){return jdbc.sql("SELECT * FROM collection_schedule ORDER BY next_at,id").query((r,n)->new Schedule(r.getString("id"),r.getString("kind"),r.getString("target_id"),r.getString("provider"),r.getBoolean("enabled"),r.getLong("version"),r.getTimestamp("next_at").toInstant().toString(),adapter(r.getString("provider"),r.getString("kind")).isPresent()?"READY":"WAITING_SOURCE")).list();}
    List<Job> jobs(){return jdbc.sql("SELECT * FROM collection_job ORDER BY created_at DESC,id LIMIT 100").query((r,n)->new Job(r.getString("id"),r.getString("schedule_id"),r.getLong("schedule_version"),r.getLong("target_version"),r.getString("status"),r.getInt("attempts"),r.getString("reason"))).list();}
    Schedule configure(String kind,String id,String provider,boolean enabled,Long version){return tx.execute(s->{
        lock();port(kind); if(enabled)port(kind).prepare(id);
        if(provider==null||!provider.matches("[A-Za-z0-9_-]{1,100}"))throw error(400,"来源标识无效");
        var old=schedules().stream().filter(v->v.kind().equals(kind)&&v.targetId().equals(id)).findFirst();
        if(old.isPresent()&&(version==null||old.get().version()!=version))throw error(409,"调度配置已更新");
        if(enabled&&adapter(provider,kind).isEmpty())throw error(409,"来源待接入或未经批准，不能启用真实采集");
        String sid=old.map(Schedule::id).orElse(UUID.randomUUID().toString());
        if(old.isEmpty())jdbc.sql("INSERT INTO collection_schedule(id,kind,target_id,provider,enabled,next_at) VALUES(:sid,:kind,:target,:provider,:enabled,:now)").param("sid",sid).param("kind",kind).param("target",id).param("provider",provider).param("enabled",enabled).param("now",Timestamp.from(Instant.now())).update();
        else jdbc.sql("UPDATE collection_schedule SET provider=:provider,enabled=:enabled,version=version+1,next_at=:now WHERE id=:sid").param("provider",provider).param("enabled",enabled).param("sid",sid).param("now",Timestamp.from(Instant.now())).update();
        jdbc.sql("UPDATE collection_job SET status='CANCELLED',reason='配置已变更' WHERE schedule_id=:sid AND status IN ('QUEUED','RETRY','RUNNING')").param("sid",sid).update();
        return schedules().stream().filter(v->v.id().equals(sid)).findFirst().orElseThrow();
    });}
    /** Business action: enqueue a bounded public-page read; never performs network I/O in the request. */
    public void enqueue(String kind,String targetId){
        if(adapter(AmazonPublicAdapter.SOURCE,kind).isEmpty())return;
        tx.executeWithoutResult(s->{lock();port(kind).prepare(targetId);
            var existing=schedules().stream().filter(v->v.kind().equals(kind)&&v.targetId().equals(targetId)).findFirst();
            if(existing.isEmpty()){configure(kind,targetId,AmazonPublicAdapter.SOURCE,true,null);return;}
            var schedule=existing.get();
            if(!schedule.provider().equals(AmazonPublicAdapter.SOURCE)||!schedule.enabled()){configure(kind,targetId,AmazonPublicAdapter.SOURCE,true,schedule.version());return;}
            if(jdbc.sql("SELECT COUNT(*) FROM collection_job WHERE schedule_id=:id AND status IN ('QUEUED','RUNNING','RETRY')").param("id",schedule.id()).query(Long.class).single()==0)
                jdbc.sql("UPDATE collection_schedule SET next_at=:now WHERE id=:id").param("id",schedule.id()).param("now",Timestamp.from(Instant.now())).update();
        });
    }
    List<Map<String,Object>> targetStatus(String kind){return jdbc.sql("SELECT s.target_id AS targetId,s.next_at AS nextAt,s.enabled,j.status,j.reason,j.created_at AS createdAt FROM collection_schedule s LEFT JOIN collection_job j ON j.id=(SELECT j2.id FROM collection_job j2 WHERE j2.schedule_id=s.id ORDER BY j2.created_at DESC,j2.id DESC LIMIT 1) WHERE s.kind=:kind").param("kind",kind).query().listOfRows();}
    /** Called periodically; network collection happens outside transactions. Leases are fenced at result commit. */
    public void tick(){
        tx.executeWithoutResult(s->{lock();Instant now=Instant.now();
            jdbc.sql("UPDATE collection_job SET status=CASE WHEN attempts>=4 THEN 'FAILED' ELSE 'RETRY' END,reason='执行租约过期',available_at=:now,lease_token=NULL WHERE status='RUNNING' AND lease_until<:now").param("now",Timestamp.from(now)).update();
            for(Schedule schedule:schedules()){
                if(!schedule.enabled()||Instant.parse(schedule.nextAt()).isAfter(now)||adapter(schedule.provider(),schedule.kind()).isEmpty())continue;
                if(jdbc.sql("SELECT COUNT(*) FROM collection_job WHERE schedule_id=:id AND status IN ('QUEUED','RETRY','RUNNING')").param("id",schedule.id()).query(Long.class).single()>0)continue;
                CollectionAdapter.Target target;
                try{target=port(schedule.kind()).prepare(schedule.targetId());}catch(ResponseStatusException e){continue;}
                jdbc.sql("INSERT INTO collection_job(id,schedule_id,schedule_version,target_version,status,available_at) VALUES(:id,:sid,:version,:tv,'QUEUED',:now)")
                    .param("id",UUID.randomUUID().toString()).param("sid",schedule.id()).param("version",schedule.version()).param("tv",target.version()).param("now",Timestamp.from(now)).update();
                Instant next=now.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("America/Los_Angeles")).plusHours(3).plusSeconds(Math.floorMod(schedule.id().hashCode(),3600)).toInstant();
                jdbc.sql("UPDATE collection_schedule SET next_at=:next WHERE id=:id").param("next",Timestamp.from(next)).param("id",schedule.id()).update();
            }
        });
        Claim claim=tx.execute(s->{lock();var now=Timestamp.from(Instant.now());
            var id=jdbc.sql("SELECT id FROM collection_job WHERE status IN ('QUEUED','RETRY') AND available_at<=:now ORDER BY available_at,id LIMIT 1").param("now",now).query(String.class).optional();
            if(id.isEmpty())return null;
            Job job=jdbc.sql("SELECT * FROM collection_job WHERE id=:id").param("id",id.get()).query((r,n)->new Job(r.getString("id"),r.getString("schedule_id"),r.getLong("schedule_version"),r.getLong("target_version"),r.getString("status"),r.getInt("attempts"),r.getString("reason"))).single();
            if(job==null)return null;
            Schedule schedule=schedules().stream().filter(v->v.id().equals(job.scheduleId())).findFirst().orElseThrow();
            CollectionAdapter.Target target;
            try{target=port(schedule.kind()).prepare(schedule.targetId());}catch(ResponseStatusException e){cancel(job.id());return null;}
            if(!schedule.enabled()||schedule.version()!=job.scheduleVersion()||target.version()!=job.targetVersion()){cancel(job.id());return null;}
            if(adapter(schedule.provider(),schedule.kind()).isEmpty()){cancel(job.id());return null;}
            String lease=UUID.randomUUID().toString();
            jdbc.sql("UPDATE collection_job SET status='RUNNING',attempts=attempts+1,lease_token=:lease,lease_until=:until WHERE id=:id")
                .param("id",job.id()).param("lease",lease).param("until",Timestamp.from(Instant.now().plusSeconds(300))).update();
            return new Claim(job,schedule,target,lease);
        });
        if(claim==null)return;
        try{
            var observation=adapter(claim.schedule().provider(),claim.schedule().kind()).orElseThrow().collect(claim.target());
            tx.executeWithoutResult(s->{lock();if(!owns(claim))return;
                Schedule current=schedules().stream().filter(v->v.id().equals(claim.schedule().id())).findFirst().orElseThrow();
                CollectionAdapter.Target target;
                try{target=port(current.kind()).prepare(current.targetId());}catch(ResponseStatusException e){cancel(claim.job().id());return;}
                if(adapter(current.provider(),current.kind()).isEmpty()||!current.enabled()||current.version()!=claim.job().scheduleVersion()||target.version()!=claim.job().targetVersion()){cancel(claim.job().id());return;}
                if(observation==null||observation.observedAt()==null||observation.observedAt().isAfter(Instant.now().plusSeconds(60))||observation.observedAt().isBefore(Instant.parse("2000-01-01T00:00:00Z")))throw error(400,"来源观测时间无效");
                boolean partialProduct=current.kind().equals("PRODUCT")&&current.provider().equals(AmazonPublicAdapter.SOURCE)&&"PARTIAL".equals(observation.quality());
                if((!"COMPLETE".equals(observation.quality())&&!partialProduct)||!observation.pagesComplete()){finish(claim,"PARTIAL","分页或字段不完整，保留原基线");notify(claim,"采集不完整，保留原基线");return;}
                String message=port(current.kind()).apply(target,current.provider(),claim.job().id(),observation);
                finish(claim,partialProduct?"PARTIAL":"SUCCEEDED",partialProduct?"部分字段未观测，保留其历史值；已更新本次可见字段":"");if(message!=null&&!message.isBlank())notify(claim,message);
            });
        }catch(Exception e){tx.executeWithoutResult(s->{lock();if(!owns(claim))return;
            boolean retry=e instanceof CollectionAdapter.CollectionFailure f&&f.retryable()&&claim.job().attempts()<3;
            long delay=retry?new long[]{60,300,1800}[Math.min(claim.job().attempts(),2)]:0;
            if(e instanceof CollectionAdapter.CollectionFailure f)delay=Math.max(delay,Math.min(Math.max(0,f.retryAfterSeconds()),86400));
            finish(claim,retry?"RETRY":"FAILED",e instanceof CollectionAdapter.CollectionFailure ? e.getMessage() : "采集或契约校验失败，保留原基线");
            jdbc.sql("UPDATE collection_job SET available_at=:at WHERE id=:id").param("id",claim.job().id()).param("at",Timestamp.from(Instant.now().plusSeconds(delay))).update();
            if(!retry)notify(claim,"采集失败，请核查来源和授权");
        });}
    }
    private Optional<CollectionAdapter> adapter(String provider,String kind){return adapters.stream().filter(a->a.provider().equals(provider)&&a.approved()&&a.kinds().contains(kind)).findFirst();}
    private CollectionTarget port(String kind){return targets.stream().filter(t->t.kind().equals(kind)).findFirst().orElseThrow(()->error(400,"不支持的监控对象"));}
    private boolean owns(Claim claim){return jdbc.sql("SELECT COUNT(*) FROM collection_job WHERE id=:id AND status='RUNNING' AND lease_token=:lease AND lease_until>=:now").param("id",claim.job().id()).param("lease",claim.lease()).param("now",Timestamp.from(Instant.now())).query(Long.class).single()>0;}
    private void cancel(String id){jdbc.sql("UPDATE collection_job SET status='CANCELLED',reason='目标或配置已变更' WHERE id=:id").param("id",id).update();}
    private void finish(Claim claim,String status,String reason){jdbc.sql("UPDATE collection_job SET status=:status,reason=:reason,lease_token=NULL WHERE id=:id").param("id",claim.job().id()).param("status",status).param("reason",reason).update();}
    private void notify(Claim c,String message){
        String capability=c.schedule().kind().equals("STORE")?"STORE":"PRODUCT";
        var users=jdbc.sql("SELECT s.user_id FROM collection_subscription s JOIN iam_user u ON u.id=s.user_id WHERE s.enabled=TRUE AND u.status='ENABLED'").query(String.class).list();
        for(String user:users)jdbc.sql("INSERT INTO collection_notice(id,user_id,job_id,kind,target_id,message) VALUES(:id,:user,:job,:kind,:target,:message)")
            .param("id",UUID.randomUUID().toString()).param("user",user).param("job",c.job().id()).param("kind",capability).param("target",c.target().id()).param("message",message.substring(0,Math.min(200,message.length()))).update();
    }
    private ResponseStatusException error(int status,String message){return new ResponseStatusException(HttpStatus.valueOf(status),message);}
}
