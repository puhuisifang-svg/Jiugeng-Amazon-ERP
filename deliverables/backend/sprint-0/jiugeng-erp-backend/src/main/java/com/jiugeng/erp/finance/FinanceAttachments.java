package com.jiugeng.erp.finance;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static com.jiugeng.erp.finance.FinanceController.require;

@RestController @Profile("!mock") @RequestMapping("/api/v1/finance/bills/{billId}/attachments") @Transactional
class FinanceAttachments {
 private final JdbcClient jdbc;private final FinanceController bills;
 FinanceAttachments(JdbcClient jdbc,FinanceController bills){this.jdbc=jdbc;this.bills=bills;}
 record Attachment(String id,String name,long size,String mediaType){}
 @GetMapping @PreAuthorize("hasAuthority('finance.read')") @Transactional(readOnly=true)
 List<Attachment> list(@PathVariable String billId){bills.bill(billId);return jdbc.sql("SELECT id,file_name,byte_size,media_type FROM finance_attachment WHERE bill_id=:id ORDER BY created_at,id").param("id",billId).query((r,n)->new Attachment(r.getString(1),r.getString(2),r.getLong(3),r.getString(4))).list();}
 @PostMapping @PreAuthorize("hasAuthority('finance.write')")
 Attachment upload(@PathVariable String billId,@RequestParam MultipartFile file,@AuthenticationPrincipal AuthenticatedActor actor)throws java.io.IOException {
  lock(billId);require(!bills.bill(billId).voided(),"已作废账单不可上传凭证");require(file.getSize()>0&&file.getSize()<=5*1024*1024,"单个凭证须为1字节至5MB");require(list(billId).size()<5,"每笔账单最多5个凭证");
  byte[] bytes=file.getBytes();String name=FinanceController.text(file.getOriginalFilename(),200,true);require(!name.contains("/")&&!name.contains("\\")&&!name.chars().anyMatch(Character::isISOControl),"文件名不合法");
  String lower=name.toLowerCase(Locale.ROOT),type=null;
  if(lower.endsWith(".pdf")&&bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))type="application/pdf";
  if(lower.endsWith(".png")&&bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))type="image/png";
  if((lower.endsWith(".jpg")||lower.endsWith(".jpeg"))&&bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255)type="image/jpeg";
  require(type!=null,"凭证仅支持内容匹配的 PNG、JPG、PDF");String id=UUID.randomUUID().toString();
  jdbc.sql("INSERT INTO finance_attachment(id,bill_id,file_name,media_type,file_data,byte_size) VALUES(:id,:bill,:name,:type,:data,:size)").param("id",id).param("bill",billId).param("name",name).param("type",type).param("data",bytes).param("size",bytes.length).update();
  Attachment a=new Attachment(id,name,bytes.length,type);bills.record(actor,"FINANCE_ATTACHMENT_ADD",billId,null,a);return a;
 }
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('finance.read')") @Transactional(readOnly=true)
 ResponseEntity<byte[]> download(@PathVariable String billId,@PathVariable String id){bills.bill(billId);return jdbc.sql("SELECT file_data,file_name,media_type FROM finance_attachment WHERE id=:id AND bill_id=:bill").param("id",id).param("bill",billId).query((r,n)->ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(r.getString(2),StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(r.getString(3))).body(r.getBytes(1))).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"凭证不存在"));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('finance.attachment.remove')")
 void remove(@PathVariable String billId,@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor actor){lock(billId);require(!bills.bill(billId).voided(),"已作废账单不可移除凭证");var before=list(billId).stream().filter(a->a.id().equals(id)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"凭证不存在"));jdbc.sql("DELETE FROM finance_attachment WHERE bill_id=:bill AND id=:id").param("bill",billId).param("id",id).update();bills.record(actor,"FINANCE_ATTACHMENT_REMOVE",billId,before,null);}
 private void lock(String id){jdbc.sql("SELECT id FROM finance_bill WHERE id=:id FOR UPDATE").param("id",id).query(String.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"账单不存在"));}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"操作失败")));}
}
