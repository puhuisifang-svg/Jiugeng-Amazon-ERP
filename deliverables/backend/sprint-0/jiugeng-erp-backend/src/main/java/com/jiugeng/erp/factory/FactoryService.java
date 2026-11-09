package com.jiugeng.erp.factory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.materialinventory.FactoryOperationDirectory;
import com.jiugeng.erp.product.ProductLookup;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Profile("!mock") @Transactional
class FactoryService {
    final JdbcClient db; final ObjectMapper json; final AuditTrail audit; final FactoryOperationDirectory operations; final ProductLookup products;
    FactoryService(JdbcClient db,ObjectMapper json,AuditTrail audit,FactoryOperationDirectory operations,ProductLookup products){this.db=db;this.json=json;this.audit=audit;this.operations=operations;this.products=products;}
    List<Map<String,Object>> operationList(){return operations.list();}
    static final Set<String> KINDS=Set.of("employees","rates","reports","payroll");
    record Page(List<Map<String,Object>> items,long totalCount,BigDecimal amount){}
    static String str(Map<String,Object> d,String k){return Objects.toString(d.get(k),"").trim();}
    static String text(Map<String,Object>d,String k,int max,boolean required){String v=str(d,k);check((!required||!v.isBlank())&&v.length()<=max,k+" 内容缺失或过长");return v;}
    static void check(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    static BigDecimal dec(Object x){try{return new BigDecimal(Objects.toString(x,"0"));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"数量或金额格式不正确");}}
    static BigDecimal money(Object x){var v=dec(x);check(v.scale()<=2&&v.abs().compareTo(new BigDecimal("999999999999"))<=0,"金额最多两位小数且不能超出范围");return v.setScale(2);}
    static LocalDate date(String s){try{return LocalDate.parse(s);}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"日期格式不正确");}}
    static String month(String s){try{return YearMonth.parse(s).toString();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"工资月份格式不正确");}}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Map<String,Object>d,String k){Object v=d.get(k);check(v==null||v instanceof List<?>,"明细格式无效");if(v==null)return new ArrayList<>();var list=(List<?>)v;check(list.size()<=200,"单次明细最多200行");check(list.stream().allMatch(x->x instanceof Map<?,?>),"明细格式无效");return (List<Map<String,Object>>)v;}
    String encode(Object d){try{return json.writeValueAsString(d);}catch(Exception e){throw new IllegalStateException(e);}}
    Map<String,Object> decode(String s){try{return json.readValue(s,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new IllegalStateException(e);}}
    Map<String,Object> get(String kind,String id){check(KINDS.contains(kind),"业务类型无效");return db.sql("SELECT document_json,version FROM factory_record WHERE id=:id AND kind=:kind").param("id",id).param("kind",kind).query((r,n)->{var d=decode(r.getString(1));d.put("version",r.getLong(2));return d;}).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"记录不存在"));}
    List<Map<String,Object>> find(String kind,String employee,String prefix){return db.sql("SELECT document_json,version FROM factory_record WHERE kind=:k AND (:e='' OR employee_id=:e) AND date_key LIKE :m ORDER BY date_key,id").param("k",kind).param("e",employee).param("m",prefix+"%").query((r,n)->{var d=decode(r.getString(1));d.put("version",r.getLong(2));return d;}).list();}
    Page list(String kind,String q,String status,String employee,String from,String to,int page,int size){
        check(KINDS.contains(kind)&&page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size)&&q.length()<=200,"查询参数无效");
        String where=" FROM factory_record WHERE kind=:k AND status<>'ARCHIVED' AND (:s='' OR status=:s) AND (:e='' OR employee_id=:e) AND (:f='' OR date_key>=:f) AND (:t='' OR date_key<=:t) AND LOWER(search_text) LIKE :q ESCAPE '!'";
        var p=Map.of("k",kind,"s",status,"e",employee,"f",from,"t",to,"q","%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        long count=db.sql("SELECT COUNT(*)"+where).params(p).query(Long.class).single();
        var amount=db.sql("SELECT COALESCE(SUM(amount),0)"+where+" AND status<>'VOID'").params(p).query(BigDecimal.class).single();
        var items=db.sql("SELECT id"+where+" ORDER BY updated_at DESC,id LIMIT :size OFFSET :offset").params(p).param("size",size).param("offset",(long)page*size).query(String.class).list().stream().map(id->decorate(kind,get(kind,id))).toList();
        return new Page(items,count,amount);
    }
    Map<String,Object> decorate(String kind,Map<String,Object>d){if(kind.equals("payroll")){var employee=get("employees",str(d,"employeeId"));d.put("bankName",str(employee,"bankName"));d.put("bankAccount",str(employee,"bankAccount"));BigDecimal paid=payments(d);d.put("paid",paid);d.put("remaining",money(d.get("amount")).subtract(paid));d.put("stale",!Boolean.TRUE.equals(d.get("historical"))&&!str(d,"sourceSignature").equals(signature(str(d,"employeeId"),str(d,"date"))));}return d;}
    List<Map<String,Object>> history(String kind,String id,int page,int size){get(kind,id);check(page>=0&&page<=100000&&Set.of(10,20,50,100).contains(size),"分页参数无效");return db.sql("SELECT id,action,actor_name,reason,created_at,before_json,after_json FROM factory_event WHERE record_id=:id ORDER BY created_at DESC,id DESC LIMIT :size OFFSET :offset").param("id",id).param("size",size).param("offset",(long)page*size).query((r,n)->Map.<String,Object>of("id",r.getString(1),"action",r.getString(2),"actor",r.getString(3),"reason",r.getString(4),"time",r.getTimestamp(5).toInstant().toString(),"before",snapshot(r.getString(6)),"after",snapshot(r.getString(7)))).list();}
    Map<String,Object> snapshot(String value){var d=decode(value);if(d.containsKey("payments")){var paid=payments(d);d.put("paid",paid);d.put("remaining",dec(d.get("amount")).subtract(paid));}return d;}
    long historyCount(String id){return db.sql("SELECT COUNT(*) FROM factory_event WHERE record_id=:id").param("id",id).query(Long.class).single();}
    Map<String,Object> mutate(String kind,String action,Map<String,Object> in,AuthenticatedActor actor){
        db.sql("SELECT id FROM factory_lock WHERE id=1 FOR UPDATE").query(Integer.class).single();
        String key=text(in,"requestId",64,true);String request=encode(Map.of("kind",kind,"action",action,"input",in));
        var previous=db.sql("SELECT actor_id,request_json,response_json FROM factory_request WHERE request_id=:id").param("id",key).query((r,n)->List.of(r.getString(1),r.getString(2),r.getString(3))).optional();
        if(previous.isPresent()){check(previous.get().get(0).equals(actor.userId())&&previous.get().get(1).equals(request),"请求标识已使用，请刷新后重试");return decode(previous.get().get(2));}
        Map<String,Object> result;
        if(kind.equals("employees"))result=employee(action,in,actor);
        else if(kind.equals("rates"))result=rate(action,in,actor);
        else if(kind.equals("reports"))result=report(action,in,actor);
        else if(kind.equals("payroll"))result=payroll(action,in,actor);
        else throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"未知功能");
        db.sql("INSERT INTO factory_request(request_id,actor_id,request_json,response_json) VALUES(:id,:a,:q,:r)").param("id",key).param("a",actor.userId()).param("q",request).param("r",encode(result)).update();return result;
    }
    Map<String,Object> existing(String kind,Map<String,Object>in){String id=str(in,"id");if(id.isEmpty())return null;var d=get(kind,id);if(!dec(d.get("version")).equals(dec(in.get("version"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"记录已更新，请刷新后重试");return d;}
    Map<String,Object> save(String kind,Map<String,Object>before,Map<String,Object>d,String action,String reason,AuthenticatedActor a){
        String id=before==null?UUID.randomUUID().toString():str(before,"id");long version=before==null?0:dec(before.get("version")).longValue()+1;
        d.put("id",id);d.put("version",version);d.put("updatedAt",Instant.now().toString());d.put("updatedBy",a.displayName());
        if(before==null){d.put("createdAt",Instant.now().toString());if(kind.equals("employees"))d.put("number",String.format("YG-%06d",db.sql("SELECT COUNT(*) FROM factory_record WHERE kind='employees'").query(Long.class).single()+1));}
        String search=String.join(" ",List.of(str(d,"number"),str(d,"name"),str(d,"team"),str(d,"phone"),str(d,"project"),str(d,"operation"),str(d,"employeeName"),str(d,"remark"),str(d,"skuCode")));
        var params=new HashMap<String,Object>();params.put("id",id);params.put("kind",kind);params.put("e",str(d,"employeeId"));params.put("date",str(d,"date"));params.put("s",str(d,"status"));params.put("search",search);params.put("amount",dec(d.get("amount")));params.put("doc",encode(d));params.put("v",version);
        if(before==null)db.sql("INSERT INTO factory_record(id,kind,employee_id,date_key,status,search_text,amount,document_json,version) VALUES(:id,:kind,:e,:date,:s,:search,:amount,:doc,:v)").params(params).update();
        else db.sql("UPDATE factory_record SET employee_id=:e,date_key=:date,status=:s,search_text=:search,amount=:amount,document_json=:doc,version=:v,updated_at=CURRENT_TIMESTAMP WHERE id=:id").params(params).update();
        db.sql("INSERT INTO factory_event(id,record_id,action,actor_name,reason,before_json,after_json) VALUES(:id,:r,:action,:a,:reason,:b,:after)").param("id",UUID.randomUUID().toString()).param("r",id).param("action",action).param("a",a.displayName()).param("reason",reason).param("b",encode(before==null?Map.of():before)).param("after",encode(d)).update();
        audit.record(new AuditTrail.BusinessOperation(a.userId(),a.loginName(),a.displayName(),"FACTORY_"+action,"FACTORY_"+kind.toUpperCase(Locale.ROOT),id,true,Map.of(),Map.of("version",version),"工厂台账操作，详情需对应工厂权限查看"));
        return decorate(kind,d);
    }
    Map<String,Object> employee(String action,Map<String,Object>in,AuthenticatedActor a){
        if(action.equals("remove")){var old=existing("employees",in);check(old!=null,"请选择员工");for(String k:List.of("reports","payroll","rates"))check(find(k,str(old,"id"),"").isEmpty(),"员工已有业务引用，请维护离职日期，不可移除");var removed=new LinkedHashMap<>(old);removed.put("status","ARCHIVED");return save("employees",old,removed,"EMPLOYEE_REMOVE",text(in,"reason",500,true),a);}
        check(action.equals("save"),"员工操作无效");var before=existing("employees",in);var d=before==null?new LinkedHashMap<String,Object>():new LinkedHashMap<>(before);
        for(String k:List.of("name","team","phone","remark","operations"))d.put(k,text(in,k,k.equals("remark")?500:100,k.equals("name")));
        if(in.containsKey("bankName"))d.put("bankName",text(in,"bankName",100,false));
        if(in.containsKey("bankAccount")){String account=text(in,"bankAccount",40,false).replaceAll("\\s","");check(account.isEmpty()||account.matches("[0-9]{1,32}"),"银行卡号请填写不超过32位的数字");d.put("bankAccount",account);}
        LocalDate start=date(str(in,"date"));String end=str(in,"endDate");if(!end.isEmpty())check(!date(end).isBefore(start),"离职日期不能早于入职日期");
        if(before!=null){check(!str(before,"status").equals("ARCHIVED"),"员工已移除");for(var r:find("reports",str(before,"id"),"")){if(str(r,"status").equals("VOID"))continue;LocalDate day=date(str(r,"date"));check(!day.isBefore(start)&&(end.isEmpty()||!day.isAfter(date(end))),"任职日期不能排除已登记的有效报工");}}
        d.put("date",start.toString());d.put("endDate",end);d.put("status",end.isEmpty()?"ACTIVE":"LEFT");return save("employees",before,d,"EMPLOYEE_SAVE","",a);
    }
    Map<String,Object> rate(String action,Map<String,Object>in,AuthenticatedActor a){
        if(action.equals("stop")){var before=existing("rates",in);check(before!=null,"请选择工价");String end=str(in,"endDate");check(!date(end).isBefore(date(str(before,"date"))),"停止日期不能早于生效日期");String reason=text(in,"reason",500,true);var d=new LinkedHashMap<>(before);d.put("endDate",end);d.put("status","STOPPED");return save("rates",before,d,"RATE_STOP",reason,a);}
        check(action.equals("save")&&str(in,"id").isEmpty(),"历史工价不可覆盖，请新增价格版本");var d=new LinkedHashMap<String,Object>();
        for(String k:List.of("project","operation","unit","remark","employeeId","productId","variantId"))d.put(k,text(in,k,k.equals("remark")?500:100,Set.of("project","operation","unit").contains(k)));
        String emp=str(d,"employeeId");d.put("employeeName",emp.isEmpty()?"全部员工":str(get("employees",emp),"name"));
        d.put("skuCode","");if(!str(d,"productId").isEmpty()){var prod=products.findById(str(d,"productId")).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"产品不存在"));check(prod.active(),"请选择启用产品");if(!str(d,"variantId").isEmpty()){var sku=products.findSku(prod.id(),str(d,"variantId")).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"SKU不存在"));check(sku.active(),"请选择启用SKU");d.put("skuCode",sku.skuCode());}}
        else check(str(d,"variantId").isEmpty(),"SKU必须属于所选产品");
        var price=dec(in.get("price"));check(price.signum()>0&&price.scale()<=6&&price.compareTo(new BigDecimal("1000000"))<0,"工价须大于0、少于100万，最多6位小数");
        d.put("price",price);d.put("date",date(str(in,"date")).toString());d.put("decimalQuantity",Boolean.TRUE.equals(in.get("decimalQuantity")));d.put("status","ACTIVE");d.put("endDate","");
        String group=encode(List.of(str(d,"project").toLowerCase(Locale.ROOT),str(d,"operation").toLowerCase(Locale.ROOT),str(d,"unit"),str(d,"productId"),str(d,"variantId")));d.put("groupKey",group);
        for(var r:find("rates","",""))check(!(str(r,"groupKey").equals(group)&&str(r,"employeeId").equals(emp)&&str(r,"date").equals(str(d,"date"))),"该日期已有适用工价，请选择新的生效日期");
        d.put("operationId",operations.ensure(str(d,"operation")));return save("rates",null,d,"RATE_CREATE",str(d,"remark"),a);
    }
    Map<String,Object> match(String rateId,String employee,String day){
        var selected=get("rates",rateId);var candidates=find("rates","","").stream().filter(r->str(r,"groupKey").equals(str(selected,"groupKey"))&&(str(r,"employeeId").isEmpty()||str(r,"employeeId").equals(employee))&&str(r,"date").compareTo(day)<=0).toList();
        var personal=candidates.stream().filter(r->str(r,"employeeId").equals(employee)).max(Comparator.comparing(r->str(r,"date")));
        var standard=candidates.stream().filter(r->str(r,"employeeId").isEmpty()).max(Comparator.comparing(r->str(r,"date")));
        var chosen=personal.orElseGet(()->standard.orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"工作日期没有有效工价")));
        if(!str(chosen,"endDate").isEmpty()&&day.compareTo(str(chosen,"endDate"))>=0){if(personal.isPresent()&&standard.isPresent())chosen=standard.get();check(str(chosen,"endDate").isEmpty()||day.compareTo(str(chosen,"endDate"))<0,"该工序已停用，请维护工作日期适用工价");}
        return chosen;
    }
    Map<String,Object> preview(Map<String,Object>in){return match(text(in,"rateId",64,true),text(in,"employeeId",64,true),date(str(in,"date")).toString());}
    void editableMonth(String employee,String day){for(var p:find("payroll",employee,day.substring(0,7))){String status=str(p,"status");check(!status.equals("LOCKED"),"该月份工资已锁定，请先进入工资详情发起更正");if(status.equals("CORRECTING"))permission("factory.payroll.correct");check(!Boolean.TRUE.equals(p.get("historical")),"该月份为历史期初工资，不能再叠加日常报工");}}
    Map<String,Object> report(String action,Map<String,Object>in,AuthenticatedActor a){
        if(action.equals("saveBatch")){var batch=rows(in,"items");check(!batch.isEmpty(),"请填写报工明细");var results=new ArrayList<Map<String,Object>>();for(var item:batch)results.add(report("save",item,a));return Map.of("items",results);}
        if(action.equals("reviewBatch")){var list=rows(in,"items");check(!list.isEmpty(),"请选择报工");var results=new ArrayList<Map<String,Object>>();for(var item:list)results.add(report("review",item,a));return Map.of("items",results);}
        var before=existing("reports",in);String reason="";var d=before==null?new LinkedHashMap<String,Object>():new LinkedHashMap<>(before);
        if(before!=null)editableMonth(str(before,"employeeId"),str(before,"date"));
        if(!action.equals("save")){
            check(before!=null,"请选择报工");String state=str(before,"status");
            if(action.equals("review")){check(Set.of("DRAFT","PENDING").contains(state),"报工状态已变化");check(!rows(before,"lines").isEmpty()&&!Boolean.TRUE.equals(before.get("pricingIncomplete")),"工价待完善，请编辑报工匹配有效工价后再核对");d.put("status","REVIEWED");}
            else if(action.equals("withdraw")){check(state.equals("REVIEWED"),"仅已核对报工可撤回");reason=text(in,"reason",500,true);d.put("status","DRAFT");}
            else if(action.equals("void")){check(!state.equals("VOID"),"报工已作废");reason=text(in,"reason",500,true);d.put("status","VOID");}
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"报工操作无效");
            return save("reports",before,d,"REPORT_"+action.toUpperCase(Locale.ROOT),reason,a);
        }
        check(before==null||Set.of("DRAFT","PENDING").contains(str(before,"status")),"已核对报工须先撤回，已作废不可修改");
        String emp=text(in,"employeeId",64,true),day=date(str(in,"date")).toString();check(!date(day).isAfter(LocalDate.now()),"不能记录未来日期报工");editableMonth(emp,day);var e=get("employees",emp);check(!str(e,"status").equals("ARCHIVED"),"员工已移除");check(day.compareTo(str(e,"date"))>=0&&(str(e,"endDate").isEmpty()||day.compareTo(str(e,"endDate"))<=0),"工作日期不在员工任职范围内");
        String status=text(in,"status",20,true);check(Set.of("DRAFT","PENDING","REVIEWED").contains(status),"报工状态无效");if(status.equals("REVIEWED"))permission("factory.reports.review");
        var inputLines=rows(in,"lines");check(!inputLines.isEmpty(),"请添加至少一条加工明细");var lines=new ArrayList<Map<String,Object>>();BigDecimal total=BigDecimal.ZERO;Set<String> seen=new HashSet<>();boolean duplicate=false, incomplete=false;
        for(var input:inputLines){Map<String,Object> r;boolean priced=true;String rateId=text(input,"rateId",64,true);get("rates",rateId);try{r=match(rateId,emp,day);}catch(ResponseStatusException ex){if(!status.equals("DRAFT"))throw ex;r=get("rates",rateId);priced=false;incomplete=true;}String group=str(r,"groupKey");if(!seen.add(group))duplicate=true;
            var quantity=dec(input.get("quantity"));check(quantity.signum()>0&&quantity.compareTo(new BigDecimal("100000000"))<0&&quantity.scale()<=(Boolean.TRUE.equals(r.get("decimalQuantity"))?3:0),"数量必须大于0且符合该工价的小数精度");
            var amount=priced?quantity.multiply(dec(r.get("price"))).setScale(2,RoundingMode.HALF_UP):BigDecimal.ZERO.setScale(2);check(amount.compareTo(new BigDecimal("1000000000000"))<0,"报工金额超出允许范围");var line=new LinkedHashMap<>(r);line.remove("id");line.remove("version");line.put("rateId",r.get("id"));line.put("priced",priced);if(!priced)line.put("price",null);line.put("quantity",quantity);line.put("amount",amount);line.put("remark",text(input,"remark",300,false));lines.add(line);total=total.add(amount);check(total.compareTo(new BigDecimal("1000000000000"))<0,"报工合计金额超出允许范围");
        }
        for(var other:find("reports",emp,day)){if(!str(other,"id").equals(str(in,"id"))&&!str(other,"status").equals("VOID")&&rows(other,"lines").stream().anyMatch(l->seen.contains(str(l,"groupKey"))))duplicate=true;}
        String remark=text(in,"remark",500,false);check(!duplicate||(Boolean.TRUE.equals(in.get("duplicateConfirmed"))&&!remark.isBlank()),"可能重复报工：请核对同日记录，并勾选确认、填写不同批次说明");
        if(before!=null)reason=text(in,"reason",500,true);
        d.put("employeeId",emp);d.put("employeeName",e.get("name"));d.put("number",e.get("number"));d.put("team",e.get("team"));d.put("date",day);d.put("lines",lines);d.put("amount",total);d.put("status",status);d.put("remark",remark);d.put("duplicateConfirmed",duplicate);d.put("pricingIncomplete",incomplete);d.put("project",String.join("、",lines.stream().map(l->str(l,"project")+" / "+str(l,"operation")).distinct().toList()));
        return save("reports",before,d,"REPORT_SAVE",reason,a);
    }
    String signature(String employee,String m){return encode(find("reports",employee,m).stream().map(r->List.of(str(r,"id"),str(r,"version"),str(r,"status"))).toList());}
    BigDecimal payments(Map<String,Object>d){return rows(d,"payments").stream().filter(p->!Boolean.TRUE.equals(p.get("voided"))).map(p->money(p.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);}
    void recalculate(Map<String,Object>d){
        var reports=find("reports",str(d,"employeeId"),str(d,"date"));var approved=reports.stream().filter(r->str(r,"status").equals("REVIEWED")).toList();
        d.put("reports",approved);d.put("days",approved.stream().map(r->str(r,"date")).distinct().count());d.put("pending",reports.stream().filter(r->Set.of("DRAFT","PENDING").contains(str(r,"status"))).count());
        BigDecimal base=approved.stream().map(r->money(r.get("amount"))).reduce(BigDecimal.ZERO,BigDecimal::add);if(Boolean.TRUE.equals(d.get("historical")))base=money(d.get("historicalAmount"));
        BigDecimal plus=BigDecimal.ZERO,minus=BigDecimal.ZERO;for(var item:rows(d,"adjustments")){var amount=money(item.get("amount"));check(amount.signum()>0,"调整金额必须大于0");check(!text(item,"remark",500,true).isBlank(),"调整说明必填");String direction=str(item,"direction");check(Set.of("ADD","SUBTRACT").contains(direction),"调整类型无效");if(direction.equals("ADD"))plus=plus.add(amount);else minus=minus.add(amount);}
        d.put("base",base);d.put("plus",plus);d.put("minus",minus);d.put("amount",base.add(plus).subtract(minus));d.put("sourceSignature",signature(str(d,"employeeId"),str(d,"date")));d.put("calculatedAt",Instant.now().toString());
    }
    Map<String,Object> payroll(String action,Map<String,Object>in,AuthenticatedActor a){
        if(action.equals("generateMonth")){String m=month(str(in,"date"));var ids=find("reports","",m).stream().filter(r->str(r,"status").equals("REVIEWED")).map(r->str(r,"employeeId")).distinct().toList();check(!ids.isEmpty(),"本月没有已核对报工，可为员工单独建立工资单");var result=new ArrayList<Map<String,Object>>();for(String e:ids){var found=find("payroll",e,m);if(found.isEmpty())result.add(payroll("generate",Map.of("employeeId",e,"date",m),a));}return Map.of("created",result.size());}
        if(action.equals("lockBatch")){var items=rows(in,"items");check(!items.isEmpty(),"请选择工资单");for(var item:items)payroll("lock",item,a);return Map.of("count",items.size());}
        var before=existing("payroll",in);var d=before==null?new LinkedHashMap<String,Object>():new LinkedHashMap<>(before);String reason="";
        if(action.equals("generate")){
            check(before==null,"不能重复创建");String employee=text(in,"employeeId",64,true),m=month(str(in,"date"));var same=find("payroll",employee,m);if(!same.isEmpty())return decorate("payroll",same.get(0));var e=get("employees",employee);check(!str(e,"status").equals("ARCHIVED"),"员工已移除");
            d.put("employeeId",employee);d.put("employeeName",e.get("name"));d.put("number",e.get("number"));d.put("team",e.get("team"));d.put("date",m);d.put("status","DRAFT");d.put("adjustments",List.of());d.put("payments",List.of());d.put("revision",0);d.put("historical",Boolean.TRUE.equals(in.get("historical")));
            if(Boolean.TRUE.equals(d.get("historical"))){check(find("reports",employee,m).isEmpty(),"历史期初月份已存在报工，不能重复计薪");d.put("historicalAmount",money(in.get("historicalAmount")));check(dec(d.get("historicalAmount")).signum()>=0,"历史金额不能为负");d.put("remark",text(in,"remark",500,true));}recalculate(d);
        }else{
            check(before!=null,"请选择工资单");String state=str(before,"status");
            switch(action){
                case "calculate" -> {check(Set.of("DRAFT","CORRECTING").contains(state),"仅草稿或更正中可重新计算");if(state.equals("CORRECTING"))permission("factory.payroll.correct");recalculate(d);}
                case "adjust" -> {check(Set.of("DRAFT","CORRECTING").contains(state),"工资已锁定，请先更正");if(state.equals("CORRECTING"))permission("factory.payroll.correct");reason=text(in,"reason",500,true);d.put("adjustments",rows(in,"adjustments"));recalculate(d);}
                case "lock" -> {
                    check(Set.of("DRAFT","CORRECTING").contains(state),"工资已锁定");check(str(before,"sourceSignature").equals(signature(str(before,"employeeId"),str(before,"date"))),"报工数据已变化，请重新计算");recalculate(d);check(dec(d.get("pending")).signum()==0,"存在未核对报工，请先处理");check(dec(d.get("amount")).signum()>=0,"应发工资为负，请核对调整项");if(state.equals("DRAFT"))check(payments(d).compareTo(dec(d.get("amount")))<=0,"预支已超过应发，请先核实发放记录");
                    d.put("status","LOCKED");d.put("revision",dec(d.get("revision")).intValue()+1);d.put("lockedAt",Instant.now().toString());
                }
                case "correct" -> {check(state.equals("LOCKED"),"只能更正已锁定工资");reason=text(in,"reason",500,true);d.put("status","CORRECTING");d.put("correctionReason",reason);}
                case "pay" -> {
                    boolean advance=Boolean.TRUE.equals(in.get("advance"));check(state.equals("LOCKED")||(advance&&state.equals("DRAFT")),"普通发放须先锁定工资；更正期间不能发放");var amount=money(in.get("amount"));check(amount.signum()>0,"发放金额必须大于0");if(state.equals("LOCKED"))check(amount.compareTo(dec(d.get("amount")).subtract(payments(d)))<=0,"发放金额超过待发余额");
                    String day=date(str(in,"paymentDate")).toString();check(!date(day).isAfter(LocalDate.now()),"发放日期不能晚于今天");String method=text(in,"method",30,true);check(Set.of("现金","银行","其他").contains(method),"发放方式无效");var items=new ArrayList<>(rows(d,"payments"));check(items.size()<200,"本工资单发放记录已达上限");items.add(new LinkedHashMap<>(Map.of("id",UUID.randomUUID().toString(),"amount",amount,"date",day,"method",method,"advance",advance,"actor",a.displayName(),"remark",text(in,"remark",500,false),"voided",false)));d.put("payments",items);
                }
                case "voidPayment" -> {reason=text(in,"reason",500,true);String id=text(in,"paymentId",64,true);var items=new ArrayList<Map<String,Object>>();boolean found=false;for(var pay:rows(d,"payments")){var copy=new LinkedHashMap<>(pay);if(str(copy,"id").equals(id)){check(!Boolean.TRUE.equals(copy.get("voided")),"该记录已作废");copy.put("voided",true);copy.put("voidReason",reason);copy.put("voidedBy",a.displayName());found=true;}items.add(copy);}check(found,"发放记录不存在");d.put("payments",items);}
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"工资操作无效");
            }
        }
        return save("payroll",before,d,"PAYROLL_"+action.toUpperCase(Locale.ROOT),reason,a);
    }
    @SuppressWarnings("unchecked") List<Map<String,Object>> payrollReports(Map<String,Object> d){return (List<Map<String,Object>>)d.getOrDefault("reports",List.of());}
    static void permission(String code){var auth=SecurityContextHolder.getContext().getAuthentication();if(auth==null||auth.getAuthorities().stream().noneMatch(x->x.getAuthority().equals(code)))throw new org.springframework.security.access.AccessDeniedException("缺少所需工厂权限");}
}
