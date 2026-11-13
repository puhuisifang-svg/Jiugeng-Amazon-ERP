package com.jiugeng.erp.customs;
import java.util.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
@RestController @Profile("!mock") @RequestMapping("/api/v1/customs-shippers") @Transactional
class CustomsShippers {
 record Shipper(String id,String name,String prefix,String address,String phone,String fax){}
 private final JdbcClient jdbc;private final AuditTrail audit;
 CustomsShippers(JdbcClient jdbc,AuditTrail audit){this.jdbc=jdbc;this.audit=audit;}
 @GetMapping @PreAuthorize("hasAuthority('products.read')")
 List<Shipper> list(){return jdbc.sql("SELECT * FROM customs_shipper ORDER BY name,id").query((r,n)->new Shipper(r.getString("id"),r.getString("name"),r.getString("prefix"),r.getString("address"),r.getString("phone"),r.getString("fax"))).list();}
 @PostMapping @PreAuthorize("hasAuthority('products.write')")
 Shipper add(@RequestBody Shipper in,@AuthenticationPrincipal AuthenticatedActor actor){
  String name=clean(in.name(),300),prefix=clean(in.prefix(),20).toUpperCase(Locale.ROOT),address=clean(in.address(),1000),phone=clean(in.phone(),100),fax=clean(in.fax(),100);
  CustomsDocuments.require(com.jiugeng.erp.ChinaPhone.contact(phone),com.jiugeng.erp.ChinaPhone.MESSAGE);
  CustomsDocuments.require(!name.isBlank(),"请填写公司名称");CustomsDocuments.require(prefix.matches("[A-Z]{1,20}"),"公司简称请填写1至20位英文字母，如XMH");
  String id=UUID.randomUUID().toString();jdbc.sql("INSERT INTO customs_shipper(id,name,prefix,address,phone,fax) VALUES(:id,:name,:prefix,:address,:phone,:fax)").param("id",id).param("name",name).param("prefix",prefix).param("address",address).param("phone",phone).param("fax",fax).update();
  audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"CUSTOMS_SHIPPER_CREATE","CUSTOMS_SHIPPER",id,true,null,Map.of("created",true),"新增报关发货人"));return new Shipper(id,name,prefix,address,phone,fax);
 }
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('products.write')")
 Shipper update(@PathVariable String id,@RequestBody Shipper in,@AuthenticationPrincipal AuthenticatedActor actor){
  String name=clean(in.name(),300),prefix=clean(in.prefix(),20).toUpperCase(Locale.ROOT),address=clean(in.address(),1000),phone=clean(in.phone(),100),fax=clean(in.fax(),100);
  CustomsDocuments.require(com.jiugeng.erp.ChinaPhone.contact(phone),com.jiugeng.erp.ChinaPhone.MESSAGE);
  CustomsDocuments.require(!name.isBlank(),"请填写公司名称");CustomsDocuments.require(prefix.matches("[A-Z]{1,20}"),"公司简称请填写1至20位英文字母，如XMH");
  int changed=jdbc.sql("UPDATE customs_shipper SET name=:name,prefix=:prefix,address=:address,phone=:phone,fax=:fax WHERE id=:id").param("id",id).param("name",name).param("prefix",prefix).param("address",address).param("phone",phone).param("fax",fax).update();
  if(changed==0)throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"发货人不存在，请刷新后重试");
  audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"CUSTOMS_SHIPPER_UPDATE","CUSTOMS_SHIPPER",id,true,null,Map.of("updated",true),"修改报关发货人"));return new Shipper(id,name,prefix,address,phone,fax);
 }
 private String clean(String v,int max){String s=CustomsDocuments.text(v);CustomsDocuments.require(s.length()<=max&&!s.chars().anyMatch(Character::isISOControl),"发货人字段长度或格式不正确");return s;}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",e.getReason()));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","该发货人已存在，请从列表选择"));}
}
