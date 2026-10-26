package com.jiugeng.erp.watch;

import com.fasterxml.jackson.databind.JsonNode;
import com.jiugeng.erp.collection.CollectionAdapter;
import com.jiugeng.erp.collection.CollectionTarget;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component @Profile("!mock")
class WatchCollectionTarget implements CollectionTarget {
    private final JdbcClient jdbc;private final WatchRepository repository;
    WatchCollectionTarget(JdbcClient jdbc,WatchRepository repository){this.jdbc=jdbc;this.repository=repository;}
    public String kind(){return "STORE";}
    public CollectionAdapter.Target prepare(String id){
        repository.lockStore(id);var store=repository.store(id).orElseThrow();
        if(!store.status().equals("ACTIVE"))throw new ResponseStatusException(HttpStatus.CONFLICT,"店铺已暂停");
        return new CollectionAdapter.Target(kind(),id,store.storeKey(),store.version());
    }
    public String apply(CollectionAdapter.Target target,String provider,String job,CollectionAdapter.Observation o){
        JsonNode products=o.fields()==null?null:o.fields().get("products");
        if(products==null||!products.isArray()||products.isEmpty()||products.size()>10000)throw bad("店铺完整商品集合无效或为空");
        var seen=new LinkedHashMap<String,String>();
        for(JsonNode product:products){String asin=product.path("asin").asText(),title=product.path("title").asText();
            if(!asin.matches("[A-Z0-9]{10}")||title.isBlank()||title.length()>300||seen.put(asin,title)!=null)throw bad("商品标识、标题或去重契约不完整");
        }
        int pages=o.fields().path("pageCount").asInt(0),items=o.fields().path("itemCount").asInt(-1);
        if(pages<=0||items!=seen.size())throw bad("页数或商品总数与完整集合不一致");
        var last=jdbc.sql("SELECT observed_at FROM watch_auto_baseline WHERE store_id=:id AND provider=:source").param("id",target.id()).param("source",provider).query((r,n)->r.getTimestamp(1).toInstant()).optional();
        if(last.isPresent()&&!o.observedAt().isAfter(last.get()))return "";
        String runId=job;
        repository.insertRun(runId,target.id(),job,job,"SUCCEEDED",seen.size(),seen.size(),pages,true,job,"COMPLETE","PROMOTED","EXTERNAL_COMPLETE",o.observedAt(),"SYSTEM");
        jdbc.sql("UPDATE watch_run SET adapter_name=:provider WHERE id=:id").param("provider",provider).param("id",runId).update();
        // Automatic snapshots are isolated from MANUAL store baselines and prior manual source history.
        var existing=new HashSet<>(jdbc.sql("SELECT asin FROM watch_presence WHERE store_id=:id AND provider=:source").param("id",target.id()).param("source",provider).query(String.class).list());
        int events=0;LocalDate day=o.observedAt().atZone(ZoneId.of("America/Los_Angeles")).toLocalDate();
        for(var entry:seen.entrySet()){
            if(existing.contains(entry.getKey()))jdbc.sql("UPDATE watch_presence SET title=:title,last_at=:at,missing_count=0,missing_day=NULL,missing_at=NULL,last_run_id=:run WHERE store_id=:id AND provider=:source AND asin=:asin")
                .param("title",entry.getValue()).param("at",Timestamp.from(o.observedAt())).param("run",runId).param("id",target.id()).param("source",provider).param("asin",entry.getKey()).update();
            else {
                jdbc.sql("INSERT INTO watch_presence(store_id,provider,asin,title,first_at,last_at,last_run_id) VALUES(:id,:source,:asin,:title,:at,:at,:run)")
                    .param("id",target.id()).param("source",provider).param("asin",entry.getKey()).param("title",entry.getValue()).param("at",Timestamp.from(o.observedAt())).param("run",runId).update();
                if(last.isPresent()){
                    String key=target.id()+":"+entry.getKey();
                    try { key=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
                    catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
                    if(jdbc.sql("SELECT COUNT(*) FROM watch_new_product_event WHERE store_id=:id AND asin=:asin").param("id",target.id()).param("asin",entry.getKey()).query(Long.class).single()==0){
                        repository.insertEvent(UUID.randomUUID().toString(),key,target.id(),runId,entry.getKey(),entry.getValue(),o.observedAt());events++;
                    }
                }
            }
        }
        int missing=0;
        for(String asin:existing)if(!seen.containsKey(asin)){
            var prior=jdbc.sql("SELECT missing_count,missing_day FROM watch_presence WHERE store_id=:id AND provider=:source AND asin=:asin").param("id",target.id()).param("source",provider).param("asin",asin).query((r,n)->new Object[]{r.getInt(1),r.getDate(2)}).single();
            java.sql.Date previous=(java.sql.Date)prior[1];int count=(Integer)prior[0];
            if(previous==null||day.isAfter(previous.toLocalDate())){count++;if(count==2)missing++;
                jdbc.sql("UPDATE watch_presence SET missing_count=:count,missing_day=:day,missing_at=COALESCE(missing_at,:at),last_run_id=:run WHERE store_id=:id AND provider=:source AND asin=:asin")
                    .param("count",count).param("day",day).param("at",Timestamp.from(o.observedAt())).param("run",runId).param("id",target.id()).param("source",provider).param("asin",asin).update();
            }
        }
        if(last.isEmpty())jdbc.sql("INSERT INTO watch_auto_baseline(store_id,provider,observed_at) VALUES(:id,:source,:at)").param("id",target.id()).param("source",provider).param("at",Timestamp.from(o.observedAt())).update();
        else jdbc.sql("UPDATE watch_auto_baseline SET observed_at=:at WHERE store_id=:id AND provider=:source").param("id",target.id()).param("source",provider).param("at",Timestamp.from(o.observedAt())).update();
        return events+missing>0?"店铺发现 "+events+" 个新品、"+missing+" 个疑似下架商品":"";
    }
    private ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
}
