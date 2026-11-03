package com.jiugeng.erp.customs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class CustomsDocuments {
 public record Input(String documentNo,String status,Map<String,String> fields,List<Map<String,String>> items,Long version){}
 public record Document(String id,String documentNo,String status,Map<String,String> fields,List<Map<String,String>> items,long version,String updatedAt){}
 public record Summary(String id,String documentNo,String status,String seller,String buyer,String destination,int itemCount,long version,String updatedAt){}
 public record Page(List<Summary> items,long totalCount){}
 private final JdbcClient jdbc;private final ObjectMapper json;private final AuditTrail audit;
 public CustomsDocuments(JdbcClient jdbc,ObjectMapper json,AuditTrail audit){this.jdbc=jdbc;this.json=json;this.audit=audit;}
 static final Set<String> HEADERS=Set.of("contractNo","invoiceNo","date","exportDate","declarationDate","seller","sellerAddress","sellerPhone","sellerFax","buyer","buyerAddress","buyerPhone","buyerFax","producer","customsPort","recordNo","transport","voyage","billNo","supervision","taxNature","license","tradeCountry","destination","destinationPort","exitPort","packaging","terms","currency","freight","insurance","otherCharges","documents","marks","orderNo","fbaNo","warehouse","sourcePlace","exemption","signPlace","payment","tolerance","packingMarks","tradeTerms","shippingMarks","declarant","declarationCompany","relatedParty","priceInfluence","royalty","selfTax");
 static final Set<String> ITEM_FIELDS=Set.of("sku","variantId","name","nameEn","hsCode","unit","quantity","price","cartons","cartonNo","grossWeight","netWeight","origin","destination","sourcePlace","exemption","declarationElements","quantity2","unit2","quantity3","unit3");
 static final Set<String> NUMBERS=Set.of("quantity","price","cartons","grossWeight","netWeight","quantity2","quantity3");
 static void require(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 static String text(String v){return v==null?"":v.trim();}
 static BigDecimal number(Map<String,String> m,String k){return new BigDecimal(m.get(k));}
 private Map<String,String> clean(Map<String,String> values,Set<String> allowed){require(values!=null,"缺少表单资料");Map<String,String> result=new LinkedHashMap<>();for(String k:allowed){String v=text(values.get(k));require(v.length()<=4000,"字段过长："+k);require(v.chars().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),"字段含无效字符");result.put(k,v);}return result;}
 Input validate(Input in){
  String no=text(in.documentNo());require(!no.isEmpty()&&no.length()<=128,"请填写单据编号（最多128字）");require(Set.of("DRAFT","READY").contains(text(in.status())),"单据状态无效");
  Map<String,String> f=clean(in.fields(),HEADERS);require(in.items()!=null&&in.items().size()<=100,"每票最多100项商品");
  for(String key:List.of("date","exportDate","declarationDate"))if(!f.get(key).isBlank())try{LocalDate d=LocalDate.parse(f.get(key));require(d.getYear()>=2000&&d.getYear()<=2100,"日期范围为2000至2100年");}catch(java.time.format.DateTimeParseException e){require(false,"日期格式不正确");}
  List<Map<String,String>> items=new ArrayList<>();
  for(var item:in.items()){Map<String,String> row=clean(item,ITEM_FIELDS);for(String k:NUMBERS)if(!row.get(k).isBlank())try{BigDecimal v=number(row,k);require(v.signum()>=0&&v.scale()<=4&&v.compareTo(new BigDecimal("999999999"))<=0,"数量、金额或重量须为非负数，最多4位小数");if(k.equals("cartons"))require(v.stripTrailingZeros().scale()<=0,"箱数须为整数");}catch(NumberFormatException e){require(false,"数量、金额或重量格式不正确");}
   if(!row.get("grossWeight").isBlank()&&!row.get("netWeight").isBlank())require(number(row,"grossWeight").compareTo(number(row,"netWeight"))>=0,"商品毛重不能小于净重");
   if(!row.get("hsCode").isBlank())require(row.get("hsCode").matches("[0-9]{8,10}"),"商品编号须为8至10位数字");items.add(row);}
  BigDecimal total=BigDecimal.ZERO;for(var r:items)if(!r.get("quantity").isBlank()&&!r.get("price").isBlank())total=total.add(number(r,"quantity").multiply(number(r,"price")).setScale(2,java.math.RoundingMode.HALF_UP));require(total.compareTo(new BigDecimal("999999999999.99"))<=0,"本票总金额超出支持范围（最多12位整数）");
  Input result=new Input(no,in.status(),f,items,in.version());if("READY".equals(in.status()))complete(result);return result;
 }
 static void complete(Input in){
  List<String> missing=new ArrayList<>();Map<String,String> f=in.fields();
  String[][] required={{"contractNo","合同号"},{"invoiceNo","发票号"},{"date","单证日期"},{"seller","境内发货人"},{"sellerAddress","发货人地址"},{"buyer","境外收货人"},{"buyerAddress","收货人地址"},{"producer","生产销售单位"},{"destination","运抵国"},{"currency","币制"},{"terms","成交方式"},{"packaging","包装种类"}};
  for(String[] field:required)if(text(f.get(field[0])).isBlank())missing.add(field[1]);
  if(in.items().isEmpty())missing.add("至少一项商品");
  for(int i=0;i<in.items().size();i++){var r=in.items().get(i);for(String key:List.of("sku","name","hsCode","unit","quantity","price","cartons","grossWeight","netWeight","origin","declarationElements"))if(text(r.get(key)).isBlank())missing.add("商品"+(i+1)+"："+LABELS.getOrDefault(key,key));
   for(String key:List.of("quantity","price","cartons","grossWeight","netWeight"))if(!text(r.get(key)).isBlank()&&number(r,key).signum()<=0)missing.add("商品"+(i+1)+"："+LABELS.get(key)+"须大于0");
   for(String n:List.of("2","3"))if(text(r.get("quantity"+n)).isBlank()!=text(r.get("unit"+n)).isBlank())missing.add("商品"+(i+1)+"：附加数量与单位需同时填写");}
  require(missing.isEmpty(),"请完善后导出或标记已核对："+String.join("、",missing));
 }
 private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("sku","SKU"),Map.entry("name","品名"),Map.entry("hsCode","商品编号"),Map.entry("unit","单位"),Map.entry("quantity","数量"),Map.entry("price","单价"),Map.entry("cartons","箱数"),Map.entry("grossWeight","毛重"),Map.entry("netWeight","净重"),Map.entry("origin","原产国"),Map.entry("declarationElements","申报要素"));
 private String encode(Input in){try{return json.writeValueAsString(in);}catch(Exception e){throw new IllegalStateException(e);}}
 Document get(String id){return jdbc.sql("SELECT * FROM customs_document WHERE id=:id").param("id",id).query((r,n)->{try{Input v=json.readValue(r.getString("payload"),Input.class);return new Document(id,r.getString("document_no"),r.getString("status"),v.fields(),v.items(),r.getLong("version"),r.getTimestamp("updated_at").toInstant().toString());}catch(java.io.IOException e){throw new IllegalStateException(e);}}).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"报关单据不存在"));}
 Page list(String q,String status,int page,int size){require(page>=0&&page<=100000&&size>=1&&size<=100,"分页参数无效");require(q.length()<=128&&Set.of("","DRAFT","READY","VOID").contains(status),"筛选参数无效");String where=" WHERE (:status='' OR status=:status) AND LOWER(document_no) LIKE :q ESCAPE '!'";String term="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  long total=jdbc.sql("SELECT COUNT(*) FROM customs_document"+where).param("status",status).param("q",term).query(Long.class).single();
  var ids=jdbc.sql("SELECT id FROM customs_document"+where+" ORDER BY updated_at DESC,id DESC LIMIT :size OFFSET :offset").param("status",status).param("q",term).param("size",size).param("offset",page*size).query(String.class).list();
  return new Page(ids.stream().map(this::get).map(d->new Summary(d.id(),d.documentNo(),d.status(),d.fields().get("seller"),d.fields().get("buyer"),d.fields().get("destination"),d.items().size(),d.version(),d.updatedAt())).toList(),total);
 }
 Document save(String id,Input input,AuthenticatedActor actor){Input in=validate(input);Document before=id==null?null:get(id);if(before!=null){require(!before.status().equals("VOID"),"已作废单据不可修改");if(in.version()==null||in.version()!=before.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"单据已变化，请重新打开后修改");}
  if(id==null){id=UUID.randomUUID().toString();jdbc.sql("INSERT INTO customs_document(id,document_no,status,payload) VALUES(:id,:no,:status,:data)").param("id",id).param("no",in.documentNo()).param("status",in.status()).param("data",encode(in)).update();}
  else {int count=jdbc.sql("UPDATE customs_document SET document_no=:no,status=:status,payload=:data,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:version").param("id",id).param("no",in.documentNo()).param("status",in.status()).param("data",encode(in)).param("version",in.version()).update();if(count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"单据已变化，请重新打开后修改");}
  Document after=get(id);record(actor,before==null?"CUSTOMS_DOCUMENT_CREATE":"CUSTOMS_DOCUMENT_UPDATE",after);return after;
 }
 Document voidDocument(String id,long version,AuthenticatedActor actor){int count=jdbc.sql("UPDATE customs_document SET status='VOID',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:v AND status<>'VOID'").param("id",id).param("v",version).update();if(count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"单据已变化，请刷新后重试");Document d=get(id);record(actor,"CUSTOMS_DOCUMENT_VOID",d);return d;}
 void record(AuthenticatedActor actor,String action,Document d){audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"CUSTOMS_DOCUMENT",d.id(),true,null,Map.of("version",d.version(),"status",d.status(),"itemCount",d.items().size()),"报关单证资料"));}
}
