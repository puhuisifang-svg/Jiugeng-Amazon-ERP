package com.jiugeng.erp.selection;
import com.jiugeng.erp.collection.CollectionTarget;
import com.jiugeng.erp.collection.CollectionAdapter;
import com.jiugeng.erp.launch.LaunchWorkspace;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.Timestamp;

@Component @Profile("!mock")
class CandidateCollectionTarget implements CollectionTarget {
 private final JdbcClient jdbc;private final LaunchWorkspace work;
 CandidateCollectionTarget(JdbcClient jdbc,LaunchWorkspace work){this.jdbc=jdbc;this.work=work;}
 public String kind(){return "CANDIDATE";}
 public CollectionAdapter.Target prepare(String id){String asin=jdbc.sql("SELECT asin FROM selection_candidate WHERE id=:id").param("id",id).query(String.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"候选无可采集的ASIN"));return new CollectionAdapter.Target(kind(),id,asin,0);}
 public String apply(CollectionAdapter.Target target,String provider,String jobId,CollectionAdapter.Observation o){
   String d=work.encode(o.fields());
   if(jdbc.sql("SELECT COUNT(*) FROM launch_observation WHERE entity_id=:id").param("id",target.id()).query(Long.class).single()>0)jdbc.sql("UPDATE launch_observation SET observation_json=:d,observed_at=:t,provider=:p WHERE entity_id=:id").param("d",d).param("t",Timestamp.from(o.observedAt())).param("p",provider).param("id",target.id()).update();
   else jdbc.sql("INSERT INTO launch_observation(entity_id,observation_json,observed_at,provider) VALUES(:id,:d,:t,:p)").param("d",d).param("t",Timestamp.from(o.observedAt())).param("p",provider).param("id",target.id()).update();
   jdbc.sql("UPDATE collection_schedule SET enabled=FALSE WHERE kind='CANDIDATE' AND target_id=:id").param("id",target.id()).update();return "候选参考数据已更新，人工评估保持不变";
 }
}
