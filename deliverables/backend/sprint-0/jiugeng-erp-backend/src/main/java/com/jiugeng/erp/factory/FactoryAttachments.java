package com.jiugeng.erp.factory;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditTrail;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController @Profile("!mock") @RequestMapping("/api/v1/factory/{kind}/{recordId}/attachments") @PreAuthorize("isAuthenticated()") @Transactional
class FactoryAttachments {
 final FactoryService s; final org.springframework.jdbc.core.simple.JdbcClient db; final AuditTrail audit;
 FactoryAttachments(FactoryService s,org.springframework.jdbc.core.simple.JdbcClient db,AuditTrail audit){this.s=s;this.db=db;this.audit=audit;}
 void guard(String kind,String id,boolean write){FactoryService.check(Set.of("reports","payroll").contains(kind),"此记录不支持凭证");FactoryService.permission("factory."+kind+".read");if(write){if(kind.equals("payroll"))FactoryService.permission("factory.payroll.pay");else FactoryService.permission("factory.reports.write");}s.get(kind,id);}
 @GetMapping List<Map<String,Object>> list(@PathVariable String kind,@PathVariable String recordId){guard(kind,recordId,false);return db.sql("SELECT id,filename,created_at FROM factory_attachment WHERE record_id=:id ORDER BY created_at,id").param("id",recordId).query((r,n)->Map.<String,Object>of("id",r.getString(1),"name",r.getString(2),"time",r.getTimestamp(3).toInstant().toString())).list();}
 @PostMapping Map<String,String> upload(@PathVariable String kind,@PathVariable String recordId,@RequestParam("file")MultipartFile file,@AuthenticationPrincipal AuthenticatedActor actor)throws Exception{
  guard(kind,recordId,true);FactoryService.check(!file.isEmpty()&&file.getSize()<=5*1024*1024,"凭证最多5MB");byte[] bytes=file.getBytes();String media=detect(bytes);String name=Objects.requireNonNullElse(file.getOriginalFilename(),"凭证").replaceAll("[\\r\\n\\\\/]","_");FactoryService.check(name.length()<=200,"文件名称过长");
  db.sql("SELECT id FROM factory_lock WHERE id=1 FOR UPDATE").query(Integer.class).single();
  String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));var existing=db.sql("SELECT id FROM factory_attachment WHERE record_id=:r AND content_hash=:h").param("r",recordId).param("h",hash).query(String.class).optional();if(existing.isPresent())return Map.of("id",existing.get());
  FactoryService.check(db.sql("SELECT COUNT(*) FROM factory_attachment WHERE record_id=:id").param("id",recordId).query(Long.class).single()<5,"每张单据最多5份凭证");String id=UUID.randomUUID().toString();
  db.sql("INSERT INTO factory_attachment(id,record_id,filename,media_type,content_hash,content_bytes) VALUES(:id,:r,:n,:m,:h,:b)").param("id",id).param("r",recordId).param("n",name).param("m",media).param("h",hash).param("b",bytes).update();
  audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),"FACTORY_ATTACHMENT_UPLOAD","FACTORY_"+kind.toUpperCase(Locale.ROOT),recordId,true,Map.of(),Map.of("attachmentId",id),"追加工厂单据凭证"));return Map.of("id",id);
 }
 @GetMapping("/{id}") ResponseEntity<byte[]> download(@PathVariable String kind,@PathVariable String recordId,@PathVariable String id){guard(kind,recordId,false);return db.sql("SELECT filename,media_type,content_bytes FROM factory_attachment WHERE id=:id AND record_id=:r").param("id",id).param("r",recordId).query((r,n)->ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(r.getString(1),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(r.getString(2))).body(r.getBytes(3))).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"凭证不存在"));}
 static String detect(byte[] b){if(b.length>=8&&b[0]==(byte)0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G'&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return "image/png";if(b.length>=3&&b[0]==(byte)0xff&&b[1]==(byte)0xd8&&b[2]==(byte)0xff)return "image/jpeg";if(b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-')return "application/pdf";throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持有效的PNG、JPG或PDF凭证");}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"凭证操作失败")));}
}
