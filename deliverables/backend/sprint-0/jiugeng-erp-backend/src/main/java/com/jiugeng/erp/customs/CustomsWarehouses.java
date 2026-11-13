package com.jiugeng.erp.customs;
import java.util.*;
import java.time.LocalDate;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @Profile("!mock") @RequestMapping("/api/v1/customs-warehouses") @Transactional
class CustomsWarehouses {
 record Warehouse(String code,String country,String region,String city,String postalCode,String address,String source,String verifiedOn,Long version){}
 private final JdbcClient jdbc;private final AuditTrail audit;
 CustomsWarehouses(JdbcClient jdbc,AuditTrail audit){this.jdbc=jdbc;this.audit=audit;}
 @GetMapping @PreAuthorize("hasAuthority('products.read')")
 List<Warehouse> list(){return jdbc.sql("SELECT * FROM customs_warehouse ORDER BY code").query((r,n)->new Warehouse(r.getString("code"),r.getString("country"),r.getString("region"),r.getString("city"),r.getString("postal_code"),r.getString("address"),r.getString("source"),r.getString("verified_on"),r.getLong("version"))).list();}
 private String clean(String v,int max){String s=CustomsDocuments.text(v);CustomsDocuments.require(s.length()<=max&&!s.chars().anyMatch(Character::isISOControl),"仓库字段长度或格式不正确");return s;}
 private Warehouse validate(Warehouse in){
  CustomsDocuments.require(in!=null,"缺少仓库资料");String code=clean(in.code(),20).toUpperCase(Locale.ROOT),country=clean(in.country(),2).toUpperCase(Locale.ROOT),address=clean(in.address()==null?null:in.address().replaceAll("\\R", " "),1000),date=clean(in.verifiedOn(),10),source=clean(in.source(),1000);
  CustomsDocuments.require(code.matches("[A-Z0-9][A-Z0-9-]{1,19}"),"仓库编码须为2至20位字母、数字或短横线");CustomsDocuments.require(country.equals("US"),"当前仅支持美国仓库");CustomsDocuments.require(!address.isBlank(),"请填写完整收货地址");CustomsDocuments.require(!source.isBlank(),"请填写地址来源");
  if(!date.isBlank())try{LocalDate d=LocalDate.parse(date);CustomsDocuments.require(d.getYear()>=2000&&!d.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))),"核对日期不能晚于今天");}catch(java.time.format.DateTimeParseException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"核对日期格式不正确");}
  return new Warehouse(code,country,clean(in.region(),100),clean(in.city(),100),clean(in.postalCode(),20),address,source,date,in.version());
 }
 private void record(AuthenticatedActor actor,String action,Warehouse w){audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"CUSTOMS_WAREHOUSE",w.code(),true,null,Map.of("code",w.code(),"version",w.version()),"维护报关仓库地址"));}
 private Warehouse insert(Warehouse in,AuthenticatedActor actor){var w=validate(in);jdbc.sql("INSERT INTO customs_warehouse(code,country,region,city,postal_code,address,source,verified_on) VALUES(:code,:country,:region,:city,:postal,:address,:source,:verified)").param("code",w.code()).param("country",w.country()).param("region",w.region()).param("city",w.city()).param("postal",w.postalCode()).param("address",w.address()).param("source",w.source()).param("verified",w.verifiedOn()).update();var saved=new Warehouse(w.code(),w.country(),w.region(),w.city(),w.postalCode(),w.address(),w.source(),w.verifiedOn(),0L);record(actor,"CUSTOMS_WAREHOUSE_CREATE",saved);return saved;}
 @PostMapping @PreAuthorize("hasAuthority('products.write')")
 Warehouse add(@RequestBody Warehouse in,@AuthenticationPrincipal AuthenticatedActor actor){return insert(in,actor);}
 @PostMapping("/import") @PreAuthorize("hasAuthority('products.write')")
 List<Warehouse> batch(@RequestBody List<Warehouse> rows,@AuthenticationPrincipal AuthenticatedActor actor){CustomsDocuments.require(rows!=null&&!rows.isEmpty()&&rows.size()<=500,"每次导入1至500条仓库资料");var saved=new ArrayList<Warehouse>();for(var row:rows)saved.add(insert(row,actor));return saved;}
 @PutMapping("/{code}") @PreAuthorize("hasAuthority('products.write')")
 Warehouse update(@PathVariable String code,@RequestBody Warehouse in,@AuthenticationPrincipal AuthenticatedActor actor){var w=validate(in);CustomsDocuments.require(code.equals(w.code()),"仓库编码不可修改，请新增正确编码");CustomsDocuments.require(w.version()!=null&&w.version()>=0,"缺少仓库版本，请刷新");int changed=jdbc.sql("UPDATE customs_warehouse SET origin='MANUAL',region=:region,city=:city,postal_code=:postal,address=:address,source=:source,verified_on=:verified,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE code=:code AND version=:version").param("region",w.region()).param("city",w.city()).param("postal",w.postalCode()).param("address",w.address()).param("source",w.source()).param("verified",w.verifiedOn()).param("code",code).param("version",w.version()).update();if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"仓库资料已变化或不存在，请刷新后重试");var saved=new Warehouse(code,w.country(),w.region(),w.city(),w.postalCode(),w.address(),w.source(),w.verifiedOn(),w.version()+1);record(actor,"CUSTOMS_WAREHOUSE_UPDATE",saved);return saved;}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",e.getReason()));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","仓库编码重复，本次操作未保存；请编辑已有仓库，或从导入内容中移除重复编码"));}
}
