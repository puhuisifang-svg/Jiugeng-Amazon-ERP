package com.jiugeng.erp.monitoring;

import static com.jiugeng.erp.monitoring.MonitoringTypes.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MonitoringService {
    static final int LIMIT = 1000;
    static final List<String> FIELDS = List.of("title", "brand", "imageUrl", "bullets", "price", "currency", "availability");
    static final List<String> COLUMNS = List.of("brand", "price", "availability", "owner", "tags", "updated", "source");
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    MonitoringService(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    // A database lock, not a JVM lock: protects quotas/idempotency across application instances.
    private void lock() { jdbc.sql("SELECT id FROM monitor_guard WHERE id=1 FOR UPDATE").query(Integer.class).single(); }
    private long active() { return jdbc.sql("SELECT COUNT(*) FROM monitor_target WHERE enabled=TRUE").query(Long.class).single(); }

    Page<Target> list(String q, String searchBy, String status, String owner, String tag, boolean followed,
                      int page, int size, String user) {
        pagination(page, size);
        require(Set.of("ASIN", "TITLE").contains(searchBy), "不支持的搜索字段");
        require(Set.of("ALL", "ACTIVE", "PAUSED").contains(status), "不支持的监控状态");
        text(q, 2000); text(owner, 100); text(tag, 50);
        Set<String> favorites = new HashSet<>(preferences(user).followed());
        Set<String> asins = new HashSet<>(Arrays.asList(q.trim().toUpperCase(Locale.ROOT).split("[\\s,，]+")));
        require(q.isBlank() || !searchBy.equals("ASIN") || asins.size() <= 100, "最多搜索 100 个 ASIN");
        List<Target> all = jdbc.sql("SELECT * FROM monitor_target ORDER BY created_at DESC,id").query((r,n) -> target(r, favorites)).list();
        List<Target> filtered = all.stream().filter(t -> status.equals("ALL") || t.enabled() == status.equals("ACTIVE"))
                .filter(t -> !followed || t.followed())
                .filter(t -> owner.isBlank() || Objects.equals(t.ownerId(), owner) || Objects.equals(t.ownerName(), owner))
                .filter(t -> tag.isBlank() || t.tags().contains(tag))
                .filter(t -> q.isBlank() || (searchBy.equals("ASIN") ? asins.contains(t.asin())
                        : t.fields().path("title").asText("").toLowerCase(Locale.ROOT).contains(q.trim().toLowerCase(Locale.ROOT))))
                .toList();
        return new Page<>(filtered.stream().skip((long) page * size).limit(size).toList(), filtered.size(), active(), LIMIT);
    }

    @Transactional
    public List<RowResult> create(CreateInput input, AuthenticatedActor actor) {
        key(input.requestId());
        require(input.asins() != null && !input.asins().isEmpty() && input.asins().size() <= 100, "每批必须为 1～100 条 ASIN");
        List<String> tags = tags(input.tags()); String remark = text(input.remark(), 1000);
        String hash = hash(input);
        lock();
        var prior = jdbc.sql("SELECT payload_hash,result_json FROM monitor_request WHERE request_id=:id")
                .param("id", input.requestId()).query((r,n) -> Map.entry(r.getString(1), r.getString(2))).optional();
        if (prior.isPresent()) {
            conflict(prior.get().getKey().equals(hash), "请求号已用于不同内容，请重新提交");
            try { return mapper.readValue(prior.get().getValue(), new TypeReference<List<RowResult>>() {}); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        long used = active(); List<RowResult> result = new ArrayList<>();
        for (String raw : input.asins()) {
            String asin = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            if (!asin.matches("[A-Z0-9]{10}")) { result.add(new RowResult(asin.length() > 50 ? asin.substring(0,50) : asin, null,"INVALID","ASIN 必须为 10 位字母或数字")); continue; }
            var existing = jdbc.sql("SELECT id FROM monitor_target WHERE marketplace='US' AND asin=:asin").param("asin",asin).query(String.class).optional();
            if (existing.isPresent()) { result.add(new RowResult(asin,existing.get(),"EXISTS","已存在，不重复创建")); continue; }
            if (used >= LIMIT) { result.add(new RowResult(asin,null,"LIMIT","启用目标已达 1000 条，请先暂停部分目标")); continue; }
            String id = UUID.randomUUID().toString();
            jdbc.sql("INSERT INTO monitor_target(id,marketplace,asin,owner_id,owner_name,tags_json,remark) VALUES(:id,'US',:asin,:owner,:name,:tags,:remark)")
                    .param("id",id).param("asin",asin).param("owner",actor.userId()).param("name",actor.displayName())
                    .param("tags",json(tags)).param("remark",remark).update();
            used++; result.add(new RowResult(asin,id,"CREATED","已添加，等待首次观测"));
        }
        jdbc.sql("INSERT INTO monitor_request(request_id,payload_hash,result_json) VALUES(:id,:hash,:result)")
                .param("id",input.requestId()).param("hash",hash).param("result",json(result)).update();
        return result;
    }

    @Transactional
    public List<RowResult> batch(BatchInput input, AuthenticatedActor actor) {
        require(input.targets() != null && !input.targets().isEmpty() && input.targets().size() <= 100, "请选择 1～100 条记录");
        require(input.action()!=null && Set.of("ENABLE","PAUSE","ASSIGN_SELF","CLEAR_OWNER","ADD_TAGS","REMOVE_TAGS").contains(input.action()), "不支持的批量操作");
        List<String> requestedTags = tags(input.tags());
        require(input.targets().stream().allMatch(Objects::nonNull), "无效记录");
        require(input.targets().stream().map(VersionedId::id).distinct().count()==input.targets().size(), "批量记录不可重复");
        lock(); long used = active(); List<RowResult> result = new ArrayList<>();
        for (VersionedId selected : input.targets()) {
            var found = find(selected.id(), Set.of());
            if (found.isEmpty()) { result.add(new RowResult("",selected.id(),"NOT_FOUND","记录不存在")); continue; }
            Target t = found.get();
            if (t.version()!=selected.version()) { result.add(new RowResult(t.asin(),t.id(),"CONFLICT","记录已更新，请刷新重试")); continue; }
            boolean enabled = t.enabled(); String ownerId = t.ownerId(), ownerName=t.ownerName();
            LinkedHashSet<String> merged = new LinkedHashSet<>(t.tags());
            switch (input.action()) {
                case "ENABLE" -> { if (!enabled && used >= LIMIT) { result.add(new RowResult(t.asin(),t.id(),"LIMIT","启用额度不足")); continue; } if (!enabled) used++; enabled = true; }
                case "PAUSE" -> { if (enabled) used--; enabled = false; }
                case "ASSIGN_SELF" -> { ownerId=actor.userId(); ownerName=actor.displayName(); }
                case "CLEAR_OWNER" -> { ownerId=null; ownerName=null; }
                case "ADD_TAGS" -> merged.addAll(requestedTags);
                case "REMOVE_TAGS" -> merged.removeAll(requestedTags);
                default -> throw new IllegalStateException();
            }
            if (merged.size()>20) { result.add(new RowResult(t.asin(),t.id(),"INVALID","每个目标最多 20 个标签")); continue; }
            jdbc.sql("UPDATE monitor_target SET enabled=:enabled,owner_id=:owner,owner_name=:name,tags_json=:tags,version=version+1 WHERE id=:id")
                    .param("enabled",enabled).param("owner",ownerId).param("name",ownerName).param("tags",json(merged)).param("id",t.id()).update();
            result.add(new RowResult(t.asin(),t.id(),"UPDATED","已更新"));
        }
        return result;
    }

    Detail detail(String id, String user) { return new Detail(get(id, new HashSet<>(preferences(user).followed())), history(id,0,20).items()); }
    Page<Snapshot> history(String id, int page, int size) {
        pagination(page,size); get(id,Set.of());
        List<Snapshot> rows = jdbc.sql("SELECT * FROM monitor_snapshot WHERE target_id=:id ORDER BY recorded_at DESC,id DESC LIMIT :size OFFSET :offset")
                .param("id",id).param("size",size).param("offset",(long)page*size).query(this::snapshotRow).list();
        long count=jdbc.sql("SELECT COUNT(*) FROM monitor_snapshot WHERE target_id=:id").param("id",id).query(Long.class).single();
        return new Page<>(rows,count,0,0);
    }

    @Transactional
    public Snapshot snapshot(String id, SnapshotInput input) {
        key(input.requestId());
        require(input.observedAt()!=null && !input.observedAt().isAfter(Instant.now().plusSeconds(60)) && input.observedAt().isAfter(Instant.parse("2000-01-01T00:00:00Z")), "观测时间必须有效且不能晚于当前时间");
        require(input.source()!=null && Set.of("MANUAL","SYNTHETIC").contains(input.source()), "仅支持人工或合成来源");
        require(input.quality()!=null && Set.of("COMPLETE","PARTIAL","FAILED").contains(input.quality()), "无效完整性类型");
        String reason=text(input.reason(),500);
        require(input.quality().equals("COMPLETE") || !reason.isBlank(), "部分或失败观测需填写原因");
        JsonNode fields = validateFields(input.fields(),input.quality().equals("COMPLETE"));
        String hash=hash(input);
        lock(); Target target=get(id,Set.of());
        var prior=jdbc.sql("SELECT * FROM monitor_snapshot WHERE target_id=:id AND request_id=:request")
                .param("id",id).param("request",input.requestId()).query((r,n)->Map.entry(r.getString("payload_hash"),snapshotRow(r,n))).optional();
        if(prior.isPresent()) { conflict(prior.get().getKey().equals(hash),"请求号已用于不同快照"); return prior.get().getValue(); }
        conflict(target.enabled(),"目标已暂停，请先恢复监控");
        conflict(target.version()==input.version(),"记录已更新，请刷新后重新提交");
        String decision=input.quality();
        var changes=mapper.createArrayNode(); boolean promote=false;
        if(input.quality().equals("COMPLETE")) {
            if(target.observedAt()!=null && !input.observedAt().isAfter(target.observedAt())) decision="OUT_OF_ORDER";
            else {
                promote=true;
                if(target.observedAt()==null) decision="BASELINE";
                else if(!Objects.equals(target.source(),input.source())) decision="SOURCE_BASELINE";
                else {
                    for(String field:FIELDS) if(!Objects.equals(target.fields().get(field),fields.get(field))) {
                        ObjectNode change=mapper.createObjectNode();change.put("field",field);
                        change.set("before",target.fields().get(field));change.set("after",fields.get(field));changes.add(change);
                    }
                    decision=changes.isEmpty()?"UNCHANGED":"CHANGED";
                }
            }
        }
        String sid=UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO monitor_snapshot(id,target_id,request_id,payload_hash,observed_at,source,quality,decision,reason,fields_json,changes_json) VALUES(:sid,:id,:request,:hash,:at,:source,:quality,:decision,:reason,:fields,:changes)")
                .param("sid",sid).param("id",id).param("request",input.requestId()).param("hash",hash).param("at",Timestamp.from(input.observedAt()))
                .param("source",input.source()).param("quality",input.quality()).param("decision",decision).param("reason",reason)
                .param("fields",json(fields)).param("changes",json(changes)).update();
        if(promote) jdbc.sql("UPDATE monitor_target SET baseline_json=:fields,baseline_source=:source,baseline_at=:at WHERE id=:id")
                .param("fields",json(fields)).param("source",input.source()).param("at",Timestamp.from(input.observedAt())).param("id",id).update();
        jdbc.sql("UPDATE monitor_target SET last_status=:status,last_reason=:reason,last_attempt_at=CURRENT_TIMESTAMP(6),version=version+1 WHERE id=:id")
                .param("status",decision).param("reason",reason).param("id",id).update();
        return jdbc.sql("SELECT * FROM monitor_snapshot WHERE id=:id").param("id",sid).query(this::snapshotRow).single();
    }

    Preferences preferences(String user) {
        return jdbc.sql("SELECT * FROM monitor_preference WHERE user_id=:user").param("user",user)
                .query((r,n)->new Preferences(strings(r.getString("followed_json")),strings(r.getString("columns_json"))))
                .optional().orElse(new Preferences(List.of(),COLUMNS));
    }
    @Transactional public Preferences follow(String id, String user, boolean follow) {
        lock();get(id,Set.of());Preferences p=preferences(user);var ids=new LinkedHashSet<>(p.followed());
        if(follow) ids.add(id); else ids.remove(id);
        Preferences updated=new Preferences(List.copyOf(ids),p.columns());savePreferences(user,updated);return updated;
    }
    @Transactional public Preferences columns(String user,List<String> columns) {
        require(columns!=null && columns.size()<=COLUMNS.size() && columns.stream().allMatch(Objects::nonNull) && COLUMNS.containsAll(columns) && new HashSet<>(columns).size()==columns.size(),"无效列配置");
        lock();Preferences p=new Preferences(preferences(user).followed(),List.copyOf(columns));savePreferences(user,p);return p;
    }
    private void savePreferences(String user,Preferences p) {
        int changed=jdbc.sql("UPDATE monitor_preference SET followed_json=:followed,columns_json=:columns WHERE user_id=:user")
                .param("followed",json(p.followed())).param("columns",json(p.columns())).param("user",user).update();
        if(changed==0) jdbc.sql("INSERT INTO monitor_preference(user_id,followed_json,columns_json) VALUES(:user,:followed,:columns)")
                .param("user",user).param("followed",json(p.followed())).param("columns",json(p.columns())).update();
    }

    private JsonNode validateFields(JsonNode fields,boolean complete) {
        require(fields!=null && fields.isObject(),"fields 必须为对象");
        require(fields.size()<=FIELDS.size(),"不支持的观测字段");
        fields.fieldNames().forEachRemaining(k->require(FIELDS.contains(k),"不支持的观测字段："+k));
        if(complete) { require(FIELDS.stream().allMatch(fields::has),"完整快照必须包含全部字段；明确未知请传 null，未观测应选部分快照"); require(FIELDS.stream().anyMatch(k->!fields.get(k).isNull()&&!k.equals("currency")),"空结果不能建立基线"); }
        ObjectNode normalized=(ObjectNode)fields.deepCopy();
        for(String field:List.of("title","brand","imageUrl","availability","currency")) {
            JsonNode value=fields.get(field);
            if(value==null||value.isNull()) continue;
            require(value.isTextual(),field+" 必须为文本或 null");
            String s=text(value.asText(),field.equals("title")||field.equals("imageUrl")?2000:200);
            require(!s.isEmpty(),field+" 空值请明确选择未知");normalized.put(field,s);
            if(field.equals("imageUrl")) {
                try { var uri=java.net.URI.create(s);require("https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null,"图片必须为 HTTPS 地址"); }
                catch(IllegalArgumentException e){throw new MonitoringException(400,"无效图片地址");}
            }
            if(field.equals("currency")) require(s.equals("USD"),"美国站仅支持 USD");
            if(field.equals("availability")) require(Set.of("AVAILABLE","UNAVAILABLE").contains(s),"未知可售状态请使用 null");
        }
        JsonNode bullets=fields.get("bullets");
        if(bullets!=null&&!bullets.isNull()) {
            require(bullets.isArray()&&bullets.size()<=5,"五点描述最多五条");var result=mapper.createArrayNode();
            for(JsonNode bullet:bullets){require(bullet.isTextual(),"五点描述必须为文字");result.add(text(bullet.asText(),2000));}normalized.set("bullets",result);
        }
        JsonNode price=fields.get("price");
        if(price!=null&&!price.isNull()) {
            require(price.isNumber()&&price.decimalValue().signum()>=0&&price.decimalValue().compareTo(new java.math.BigDecimal("999999999.99"))<=0&&price.decimalValue().stripTrailingZeros().scale()<=2,"价格须为非负数，最多两位小数");
            require("USD".equals(fields.path("currency").asText()),"提供价格时必须同时提供 USD 币种");
            normalized.put("price",price.decimalValue().setScale(2));
        }
        // Use the same numeric node representation as persisted JSON to avoid 10 vs 10.00 false positives.
        return tree(json(normalized));
    }

    private Target get(String id,Set<String> followed){return find(id,followed).orElseThrow(()->new MonitoringException(404,"监控目标不存在"));}
    private Optional<Target> find(String id,Set<String> followed){return jdbc.sql("SELECT * FROM monitor_target WHERE id=:id").param("id",id).query((r,n)->target(r,followed)).optional();}
    private Target target(ResultSet r,Set<String> followed)throws SQLException {
        return new Target(r.getString("id"),r.getString("marketplace"),r.getString("asin"),r.getBoolean("enabled"),r.getString("owner_id"),r.getString("owner_name"),strings(r.getString("tags_json")),r.getString("remark"),r.getLong("version"),tree(r.getString("baseline_json")),r.getString("baseline_source"),instant(r,"baseline_at"),r.getString("last_status"),r.getString("last_reason"),instant(r,"last_attempt_at"),instant(r,"created_at"),followed.contains(r.getString("id")));
    }
    private Snapshot snapshotRow(ResultSet r,int n)throws SQLException {
        return new Snapshot(r.getString("id"),r.getString("target_id"),r.getString("request_id"),instant(r,"observed_at"),instant(r,"recorded_at"),r.getString("source"),r.getString("quality"),r.getString("decision"),r.getString("reason"),tree(r.getString("fields_json")),tree(r.getString("changes_json")));
    }
    private Instant instant(ResultSet r,String col)throws SQLException{Timestamp t=r.getTimestamp(col);return t==null?null:t.toInstant();}
    private JsonNode tree(String value){try{return value==null?mapper.createObjectNode():mapper.readTree(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private List<String> strings(String value){try{return mapper.readValue(value,new TypeReference<List<String>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private List<String> tags(List<String> input){if(input==null)return List.of();require(input.size()<=20,"最多 20 个标签");var result=new LinkedHashSet<String>();for(String t:input){String s=text(t,50);require(!s.isBlank(),"标签不能为空");result.add(s);}return List.copyOf(result);}
    private String text(String input,int max){String value=input==null?"":input.trim();require(value.length()<=max,"字段超过长度限制 "+max);return value;}
    private void key(String key){require(key!=null&&key.matches("[A-Za-z0-9_-]{8,100}"),"请求号须为 8～100 位字母、数字或连字符");}
    private void pagination(int page,int size){require(page>=0&&page<=100000&&Set.of(20,50,100).contains(size),"分页参数无效");}
    private void require(boolean condition,String message){if(!condition)throw new MonitoringException(400,message);}
    private void conflict(boolean condition,String message){if(!condition)throw new MonitoringException(409,message);}
}
