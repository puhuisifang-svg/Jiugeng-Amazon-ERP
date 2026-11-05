package com.jiugeng.erp.customs;

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
import static com.jiugeng.erp.customs.CustomsDocuments.require;

@RestController @Profile("!mock") @RequestMapping("/api/v1/customs-documents/{documentId}/attachments") @Transactional
class CustomsAttachments {
 private final JdbcClient jdbc;private final CustomsDocuments documents;
 CustomsAttachments(JdbcClient jdbc,CustomsDocuments documents){this.jdbc=jdbc;this.documents=documents;}
 record Attachment(String id,String category,String name,long size,String mediaType,String createdAt){}
 @GetMapping @PreAuthorize("hasAuthority('products.read')") @Transactional(readOnly=true)
 List<Attachment> list(@PathVariable String documentId){documents.get(documentId);return jdbc.sql("SELECT id,category,file_name,byte_size,media_type,created_at FROM customs_attachment WHERE document_id=:id ORDER BY created_at,id").param("id",documentId).query((r,n)->new Attachment(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getTimestamp(6).toInstant().toString())).list();}
 @PostMapping @PreAuthorize("hasAuthority('products.write')")
 Attachment upload(@PathVariable String documentId,@RequestParam String category,@RequestParam MultipartFile file,@AuthenticationPrincipal AuthenticatedActor actor)throws java.io.IOException {
  lock(documentId);require(Set.of("SCREENSHOT","INVOICE","OTHER").contains(category),"文件分类无效");require(file.getSize()>0&&file.getSize()<=10*1024*1024,"单个文件须为1字节至10MB");require(list(documentId).size()<30,"每票最多30个文件");
  String name=Objects.requireNonNullElse(file.getOriginalFilename(),"").trim();require(!name.isBlank()&&name.length()<=200&&!name.contains("/")&&!name.contains("\\")&&!name.chars().anyMatch(Character::isISOControl),"文件名不合法");
  byte[] bytes=file.getBytes();String type=mediaType(name,bytes);
  name=normalizedName(name,type);
  require(!category.equals("SCREENSHOT")||type.startsWith("image/"),"申报订单截图请上传 PNG 或 JPG 图片");
  String id=UUID.randomUUID().toString();jdbc.sql("INSERT INTO customs_attachment(id,document_id,category,file_name,media_type,file_data,byte_size) VALUES(:id,:doc,:category,:name,:type,:data,:size)").param("id",id).param("doc",documentId).param("category",category).param("name",name).param("type",type).param("data",bytes).param("size",bytes.length).update();
  changed(documentId,actor,"CUSTOMS_ATTACHMENT_ADD");return list(documentId).stream().filter(a->a.id().equals(id)).findFirst().orElseThrow();
 }
 static boolean isImageName(String name){return name.endsWith(".png")||name.endsWith(".jpg")||name.endsWith(".jpeg");}
 static String normalizedName(String name,String type){String lower=name.toLowerCase(Locale.ROOT);if(type.equals("image/png")&&!lower.endsWith(".png"))return name.substring(0,name.lastIndexOf('.'))+".png";if(type.equals("image/jpeg")&&lower.endsWith(".png"))return name.substring(0,name.lastIndexOf('.'))+".jpg";return name;}
 static String mediaType(String name,byte[] bytes){
  String lower=name.toLowerCase(Locale.ROOT);
  if(lower.endsWith(".pdf")&&bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))return "application/pdf";
  if(isImageName(lower)&&bytes.length>=8&&Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10}))return "image/png";
  if(isImageName(lower)&&bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255)return "image/jpeg";
  if(lower.endsWith(".xls")||lower.endsWith(".xlsx"))try(var workbook=org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.ByteArrayInputStream(bytes))){
   if(lower.endsWith(".xlsx")&&workbook instanceof org.apache.poi.xssf.usermodel.XSSFWorkbook)return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
   if(lower.endsWith(".xls")&&workbook instanceof org.apache.poi.hssf.usermodel.HSSFWorkbook)return "application/vnd.ms-excel";
  }catch(Exception ignored){}
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"仅支持内容匹配的 PNG、JPG、PDF、XLS、XLSX 文件");
 }
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('products.read')") @Transactional(readOnly=true)
 ResponseEntity<byte[]> download(@PathVariable String documentId,@PathVariable String id){documents.get(documentId);return jdbc.sql("SELECT file_data,file_name,media_type FROM customs_attachment WHERE id=:id AND document_id=:doc").param("id",id).param("doc",documentId).query((r,n)->ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(r.getString(2),StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(r.getString(3))).body(r.getBytes(1))).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在"));}
 @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('products.write')")
 ResponseEntity<Void> remove(@PathVariable String documentId,@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor actor){lock(documentId);int count=jdbc.sql("DELETE FROM customs_attachment WHERE document_id=:doc AND id=:id").param("doc",documentId).param("id",id).update();if(count!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在");changed(documentId,actor,"CUSTOMS_ATTACHMENT_REMOVE");return ResponseEntity.noContent().build();}
 private void lock(String id){jdbc.sql("SELECT id FROM customs_document WHERE id=:id AND deleted_at IS NULL FOR UPDATE").param("id",id).query(String.class).optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"单据不存在"));require(!documents.get(id).status().equals("VOID"),"已作废单据不可修改文件");}
 private void changed(String id,AuthenticatedActor actor,String action){jdbc.sql("UPDATE customs_document SET version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("id",id).update();documents.record(actor,action,documents.get(id));}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"操作失败")));}
}
