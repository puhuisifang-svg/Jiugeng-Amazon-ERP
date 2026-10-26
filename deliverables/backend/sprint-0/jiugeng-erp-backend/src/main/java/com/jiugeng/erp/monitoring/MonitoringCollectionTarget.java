package com.jiugeng.erp.monitoring;
import com.jiugeng.erp.collection.CollectionAdapter;
import com.jiugeng.erp.collection.CollectionTarget;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Component @Profile("!mock")
class MonitoringCollectionTarget implements CollectionTarget {
    private final JdbcClient jdbc;private final MonitoringService service;
    MonitoringCollectionTarget(JdbcClient jdbc,MonitoringService service){this.jdbc=jdbc;this.service=service;}
    public String kind(){return "PRODUCT";}
    public CollectionAdapter.Target prepare(String id){
        jdbc.sql("SELECT id FROM monitor_guard WHERE id=1 FOR UPDATE").query(Integer.class).single();
        return jdbc.sql("SELECT asin,version,enabled FROM monitor_target WHERE id=:id FOR UPDATE").param("id",id).query((r,n)->{
            if(!r.getBoolean("enabled"))throw new ResponseStatusException(HttpStatus.CONFLICT,"目标已暂停");
            return new CollectionAdapter.Target(kind(),id,r.getString("asin"),r.getLong("version"));
        }).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"目标不存在"));
    }
    public String apply(CollectionAdapter.Target target,String provider,String jobId,CollectionAdapter.Observation o){
        var saved=service.snapshotValidated(target.id(),new MonitoringTypes.SnapshotInput(jobId,target.version(),o.observedAt(),"EXTERNAL:"+provider,o.quality(),o.fields(),""),true);
        return saved.decision().equals("CHANGED")?"监控商品有新的字段变化":"";
    }
}
