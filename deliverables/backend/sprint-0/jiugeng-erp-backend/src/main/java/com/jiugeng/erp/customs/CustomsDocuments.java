package com.jiugeng.erp.customs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.hs.DeclarationSchema;
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
 public record Document(String id,String documentNo,String status,Map<String,String> fields,List<Map<String,String>> items,long version,String updatedAt,String fileStatus){
  public Document(String id,String documentNo,String status,Map<String,String> fields,List<Map<String,String>> items,long version,String updatedAt){this(id,documentNo,status,fields,items,version,updatedAt,"PENDING_FILES");}
 }
 public record Summary(String id,String documentNo,String status,String seller,String buyer,String destination,String fbaNo,String warehouse,String cartonCount,String freight,String fileStatus,int itemCount,long version,String updatedAt){}
 public record Page(List<Summary> items,long totalCount){}
 private final JdbcClient jdbc;private final ObjectMapper json;private final AuditTrail audit;
 public CustomsDocuments(JdbcClient jdbc,ObjectMapper json,AuditTrail audit){this.jdbc=jdbc;this.json=json;this.audit=audit;}
 static final Set<String> HEADERS=Set.of("shipperId","ruleVersion","sellerPrefix","grossWeight","netWeight","contractNo","invoiceNo","date","exportDate","declarationDate","seller","sellerAddress","sellerPhone","sellerFax","buyer","buyerAddress","buyerPhone","buyerFax","producer","customsPort","recordNo","transport","voyage","billNo","supervision","taxNature","license","tradeCountry","destination","destinationPort","exitPort","packaging","terms","currency","freight","insurance","otherCharges","documents","marks","orderNo","fbaNo","warehouse","sourcePlace","exemption","signPlace","payment","tolerance","packingMarks","tradeTerms","shippingMarks","declarant","declarationCompany","relatedParty","priceInfluence","royalty","selfTax");
 static final Set<String> ITEM_FIELDS=Set.of("elementFields","schemaCode","measurementUnit","elementsVersion","brandType","exportBenefit","floorCovering","fiberComposition","dimensions","weavingMethod","fixingMethod","gtin","cas","other","sku","variantId","name","nameEn","hsCode","unit","quantity","price","cartons","cartonNo","grossWeight","netWeight","origin","destination","sourcePlace","exemption","declarationElements","quantity2","unit2","quantity3","unit3");
 static final Set<String> NUMBERS=Set.of("quantity","price","cartons","grossWeight","netWeight","quantity2","quantity3");
 static void require(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 static String text(String v){return v==null?"":v.trim();}
 static BigDecimal number(Map<String,String> m,String k){return new BigDecimal(m.get(k));}
 private Map<String,String> clean(Map<String,String> values,Set<String> allowed){require(values!=null,"缺少表单资料");Map<String,String> result=new LinkedHashMap<>();for(String k:allowed){String v=text(values.get(k));require(v.length()<=((k.equals("declarationElements")||k.equals("elementFields"))?16000:k.equals("other")?8000:4000),"字段过长："+k);require(v.chars().noneMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'),"字段含无效字符");result.put(k,v);}return result;}
 Input validate(Input in){
  String no=text(in.documentNo());require(no.length()<=128,"请填写单据编号（最多128字）");require(Set.of("DRAFT","READY").contains(text(in.status())),"单据状态无效");
  Map<String,String> f=clean(in.fields(),HEADERS);require(com.jiugeng.erp.ChinaPhone.contact(f.get("sellerPhone")),com.jiugeng.erp.ChinaPhone.MESSAGE);require(in.items()!=null&&in.items().size()<=100,"每票最多100项商品");
  for(String key:List.of("date","exportDate","declarationDate"))if(!f.get(key).isBlank())try{LocalDate d=LocalDate.parse(f.get(key));require(d.getYear()>=2000&&d.getYear()<=2100,"日期范围为2000至2100年");}catch(java.time.format.DateTimeParseException e){require(false,"日期格式不正确");}
  if("2".equals(f.get("ruleVersion"))){
   String prefix=f.get("sellerPrefix").toUpperCase(Locale.ROOT),fba=f.get("fbaNo").toUpperCase(Locale.ROOT);f.put("sellerPrefix",prefix);f.put("fbaNo",fba);
   require(prefix.isBlank()||prefix.matches("[A-Z]{1,20}"),"公司简称请填写英文字母");
   require(fba.isBlank()||fba.matches("[A-Z0-9-]{4,100}"),"FBA编号至少4位，仅支持英文字母、数字和短横线");
   f.put("buyer","Amazon.com Services.Inc");f.put("producer",f.get("seller"));
   String contract=prefix.isBlank()||f.get("date").isBlank()||fba.length()<4?"":prefix+f.get("date").replace("-","")+fba.substring(fba.length()-4);
   f.put("contractNo",contract);f.put("orderNo",contract);
   if(no.isBlank())no=contract.isBlank()?"CD-"+UUID.randomUUID().toString().substring(0,18):contract;
   for(String key:List.of("grossWeight","netWeight"))if(!f.get(key).isBlank())try{BigDecimal weight=number(f,key);require(weight.signum()>=0&&weight.scale()<=4&&weight.compareTo(new BigDecimal("999999999"))<=0,"报关总重量须为非负数且最多4位小数");}catch(NumberFormatException e){require(false,"报关总重量格式不正确");}
   if(!f.get("grossWeight").isBlank()&&!f.get("netWeight").isBlank())require(number(f,"grossWeight").compareTo(number(f,"netWeight"))>=0,"报关毛重不能小于净重");
  }
  require(!no.isBlank(),"请填写单据编号");
  f.put("invoiceNo",no);f.put("currency","USD");
  for(var entry:Map.of("terms","c&f","packaging","纸制或纤维板制盒/箱","destination","美国","transport","水路运输","supervision","跨境电商出口海外仓","taxNature","一般征税","tradeCountry","美国","documents","随附单证1：跨境B2B出口单证\n随附单证2：代理报关委托协议（电子）\n随附单证3：合同；发票；装箱单").entrySet())if(f.get(entry.getKey()).isBlank())f.put(entry.getKey(),entry.getValue());
  if(f.get("payment").isBlank())f.put("payment","T/T");
  if(f.get("tolerance").isBlank())f.put("tolerance","5");
  try{BigDecimal tolerance=number(f,"tolerance");require(tolerance.signum()>=0&&tolerance.compareTo(new BigDecimal("100"))<=0,"增减比例须为0至100之间的数字");}catch(NumberFormatException e){require(false,"增减比例须为0至100之间的数字");}
  List<Map<String,String>> items=new ArrayList<>();
  for(var item:in.items()){
   var allowed=new HashSet<>(ITEM_FIELDS);
   var schema="2".equals(item.get("elementsVersion"))?DeclarationSchema.parse(item.get("elementFields")):DeclarationSchema.LEGACY;
   if("2".equals(item.get("elementsVersion"))){require(Objects.equals(item.get("schemaCode"),item.get("hsCode")),"商品HS已变化，请重新选择SKU以带入对应要素");schema.forEach(field->allowed.add(field.key()));}
   Map<String,String> row=clean(item,allowed);
   if("2".equals(row.get("elementsVersion"))){String serialized=DeclarationSchema.serialize(schema,row);require(serialized.length()<=16000,"申报要素内容过长");row.put("declarationElements",serialized);}

   if("1".equals(row.get("elementsVersion"))){String[][] elements={{"brandType","品牌类型"},{"exportBenefit","出口享惠情况"},{"floorCovering","是否为铺地制品"},{"fiberComposition","纤维成分"},{"dimensions","规格尺寸"},{"weavingMethod","织造方法"},{"fixingMethod","固定方式"},{"gtin","GTIN"},{"cas","CAS"},{"other","其他"}};var lines=new ArrayList<String>();for(var pair:elements)lines.add(pair[1]+"："+row.get(pair[0]));row.put("declarationElements",String.join("\n",lines));}
   for(String k:NUMBERS)if(!row.get(k).isBlank())try{BigDecimal v=number(row,k);require(v.signum()>=0&&v.scale()<=4&&v.compareTo(new BigDecimal("999999999"))<=0,"数量、金额或重量须为非负数，最多4位小数");if(k.equals("cartons"))require(v.stripTrailingZeros().scale()<=0,"箱数须为整数");}catch(NumberFormatException e){require(false,"数量、金额或重量格式不正确");}
   if(!row.get("grossWeight").isBlank()&&!row.get("netWeight").isBlank())require(number(row,"grossWeight").compareTo(number(row,"netWeight"))>=0,"商品毛重不能小于净重");
   if(!row.get("hsCode").isBlank())require(row.get("hsCode").matches("[0-9]{8,10}"),"商品编号须为8至10位数字");items.add(row);}
  String signingPlace=items.isEmpty()?text(f.get("sourcePlace")):text(items.getFirst().get("sourcePlace"));
  if(signingPlace.isBlank())signingPlace=text(f.get("sourcePlace"));
  f.put("signPlace",signingPlace.isBlank()?"厦门市":signingPlace);
  for(int i=0;i<items.size();i++)items.get(i).put("cartonNo",Integer.toString(i+1));
  BigDecimal total=BigDecimal.ZERO;for(var r:items)if(!r.get("quantity").isBlank()&&!r.get("price").isBlank())total=total.add(number(r,"quantity").multiply(number(r,"price")).setScale(2,java.math.RoundingMode.HALF_UP));require(total.compareTo(new BigDecimal("999999999999.99"))<=0,"本票总金额超出支持范围（最多12位整数）");
  Input result=new Input(no,in.status(),f,items,in.version());if("READY".equals(in.status()))complete(result);return result;
 }
 static void complete(Input in){
  List<String> missing=new ArrayList<>();Map<String,String> f=in.fields();
  String[][] required={{"contractNo","合同号"},{"invoiceNo","发票号"},{"date","单证日期"},{"seller","境内发货人"},{"sellerAddress","发货人地址"},{"buyer","境外收货人"},{"buyerAddress","收货人地址"},{"producer","生产销售单位"},{"destination","运抵国"},{"currency","币制"},{"terms","成交方式"},{"packaging","包装种类"}};
  for(String[] field:required)if(text(f.get(field[0])).isBlank())missing.add(field[1]);
  boolean v2="2".equals(f.get("ruleVersion"));
  if(v2){for(String key:List.of("sellerPrefix","fbaNo","warehouse","grossWeight","netWeight"))if(text(f.get(key)).isBlank())missing.add(Map.of("sellerPrefix","公司简称","fbaNo","FBA编号","warehouse","FBA仓库","grossWeight","毛重","netWeight","净重").get(key));for(String key:List.of("grossWeight","netWeight"))if(!text(f.get(key)).isBlank()&&number(f,key).signum()<=0)missing.add("报关"+LABELS.get(key)+"须大于0");}
  if(in.items().isEmpty())missing.add("至少一项商品");
  for(int i=0;i<in.items().size();i++){var r=in.items().get(i);for(String key:List.of("sku","name","hsCode","unit","quantity","price","cartons","grossWeight","netWeight","origin","declarationElements"))if(!(v2&&(key.equals("grossWeight")||key.equals("netWeight")))&&text(r.get(key)).isBlank())missing.add("商品"+(i+1)+"："+LABELS.getOrDefault(key,key));
   for(String key:List.of("quantity","price","cartons","grossWeight","netWeight"))if(!text(r.get(key)).isBlank()&&number(r,key).signum()<=0)missing.add("商品"+(i+1)+"："+LABELS.get(key)+"须大于0");
   for(String n:List.of("2","3"))if(text(r.get("quantity"+n)).isBlank()!=text(r.get("unit"+n)).isBlank())missing.add("商品"+(i+1)+"：附加数量与单位需同时填写");}
  require(missing.isEmpty(),"请完善后导出或标记已核对："+String.join("、",missing));
 }
 private static final Map<String,String> LABELS=Map.ofEntries(Map.entry("sku","SKU"),Map.entry("name","品名"),Map.entry("hsCode","商品编号"),Map.entry("unit","单位"),Map.entry("quantity","数量"),Map.entry("price","单价"),Map.entry("cartons","箱数"),Map.entry("grossWeight","毛重"),Map.entry("netWeight","净重"),Map.entry("origin","原产国"),Map.entry("declarationElements","申报要素"));
 private String encode(Input in){try{return json.writeValueAsString(in);}catch(Exception e){throw new IllegalStateException(e);}}
 Document get(String id){return jdbc.sql("SELECT * FROM customs_document WHERE id=:id AND deleted_at IS NULL").param("id",id).query((r,n)->{try{Input v=json.readValue(r.getString("payload"),Input.class);return new Document(id,r.getString("document_no"),r.getString("status"),v.fields(),v.items(),r.getLong("version"),r.getTimestamp("updated_at").toInstant().toString(),fileStatus(id));}catch(java.io.IOException e){throw new IllegalStateException(e);}}).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"报关单据不存在"));}
 static String cartonCount(List<Map<String,String>> items){
  if(items.stream().anyMatch(r->text(r.get("cartons")).isBlank()))return null;
  return items.stream().map(r->new BigDecimal(r.get("cartons"))).reduce(BigDecimal.ZERO,BigDecimal::add).toPlainString();
 }
 Page list(String q,String status,int page,int size){return list(q,status,"",page,size);}
 Page list(String q,String status,String fba,int page,int size){require(page>=0&&page<=100000&&size>=1&&size<=100,"分页参数无效");require(q.length()<=128&&fba.length()<=128&&Set.of("","DRAFT","READY","VOID","PENDING_FILES","COMPLETED").contains(status),"筛选参数无效");String completed="(EXISTS(SELECT 1 FROM customs_attachment a WHERE a.document_id=customs_document.id AND a.category='SCREENSHOT') AND EXISTS(SELECT 1 FROM customs_attachment a WHERE a.document_id=customs_document.id AND a.category='INVOICE'))";
  String where=" WHERE deleted_at IS NULL AND "+(status.equals("COMPLETED")?"status<>'VOID' AND "+completed:status.equals("PENDING_FILES")?"status<>'VOID' AND NOT "+completed:"(:status='' OR status=:status)")+" AND LOWER(document_no) LIKE :q ESCAPE '!' AND LOWER(fba_no) LIKE :fba ESCAPE '!'";
  String fbaTerm="%"+fba.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";String term="%"+q.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
  long total=jdbc.sql("SELECT COUNT(*) FROM customs_document"+where).param("status",status).param("q",term).param("fba",fbaTerm).query(Long.class).single();
  var ids=jdbc.sql("SELECT id FROM customs_document"+where+" ORDER BY updated_at DESC,id DESC LIMIT :size OFFSET :offset").param("status",status).param("q",term).param("fba",fbaTerm).param("size",size).param("offset",page*size).query(String.class).list();
  return new Page(ids.stream().map(this::get).map(d->new Summary(d.id(),d.documentNo(),d.status(),d.fields().get("seller"),d.fields().get("buyer"),d.fields().get("destination"),d.fields().get("fbaNo"),d.fields().get("warehouse"),cartonCount(d.items()),d.fields().get("freight"),fileStatus(d.id()),d.items().size(),d.version(),d.updatedAt())).toList(),total);
 }
 Document save(String id,Input input,AuthenticatedActor actor){
  if(id!=null){jdbc.sql("SELECT id FROM customs_document WHERE id=:id AND deleted_at IS NULL FOR UPDATE").param("id",id).query(String.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"报关单据不存在"));require(!fileStatus(id).equals("COMPLETED"),"已完成单据仅可查看，不允许编辑");}
  Input in=validate(input);Document before=id==null?null:get(id);if(before!=null){require(!before.status().equals("VOID"),"已作废单据不可修改");if(in.version()==null||in.version()!=before.version())throw new ResponseStatusException(HttpStatus.CONFLICT,"单据已变化，请重新打开后修改");}
  if(id==null){id=UUID.randomUUID().toString();jdbc.sql("INSERT INTO customs_document(id,document_no,status,payload,fba_no) VALUES(:id,:no,:status,:data,:fba)").param("id",id).param("no",in.documentNo()).param("status",in.status()).param("data",encode(in)).param("fba",text(in.fields().get("fbaNo"))).update();}
  else {int count=jdbc.sql("UPDATE customs_document SET document_no=:no,status=:status,payload=:data,fba_no=:fba,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND deleted_at IS NULL AND version=:version").param("id",id).param("no",in.documentNo()).param("status",in.status()).param("data",encode(in)).param("fba",text(in.fields().get("fbaNo"))).param("version",in.version()).update();if(count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"单据已变化，请重新打开后修改");}
  Document after=get(id);record(actor,before==null?"CUSTOMS_DOCUMENT_CREATE":"CUSTOMS_DOCUMENT_UPDATE",after);return after;
 }
 Document voidDocument(String id,long version,AuthenticatedActor actor){int count=jdbc.sql("UPDATE customs_document SET status='VOID',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND deleted_at IS NULL AND version=:v AND status<>'VOID'").param("id",id).param("v",version).update();if(count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"单据已变化，请刷新后重试");Document d=get(id);record(actor,"CUSTOMS_DOCUMENT_VOID",d);return d;}
 void delete(String id,long version,AuthenticatedActor actor){
  Document before=get(id);
  int count=jdbc.sql("UPDATE customs_document SET deleted_at=CURRENT_TIMESTAMP,deletion_key=id,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:version AND deleted_at IS NULL").param("id",id).param("version",version).update();
  if(count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"单据已变化，请刷新后再删除");
  record(actor,"CUSTOMS_DOCUMENT_DELETE",before);
 }
 String fileStatus(String id){return jdbc.sql("SELECT COUNT(DISTINCT category) FROM customs_attachment WHERE document_id=:id AND category IN ('SCREENSHOT','INVOICE')").param("id",id).query(Integer.class).single()==2?"COMPLETED":"PENDING_FILES";}
 static String exportName(Document d){
  String date=text(d.fields().get("date"));try{var day=LocalDate.parse(date);date=day.getMonthValue()+"-"+day.getDayOfMonth();}catch(java.time.format.DateTimeParseException ignored){}
  String prefix=text(d.fields().get("sellerPrefix")),fba=text(d.fields().get("fbaNo"));
  return (prefix+"报关单资料-"+date+"-"+(fba.isBlank()?d.documentNo():fba)).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_")+".xlsx";
 }
 void record(AuthenticatedActor actor,String action,Document d){audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"CUSTOMS_DOCUMENT",d.id(),true,null,Map.of("version",d.version(),"status",d.status(),"itemCount",d.items().size()),"报关单证资料"));}
}
