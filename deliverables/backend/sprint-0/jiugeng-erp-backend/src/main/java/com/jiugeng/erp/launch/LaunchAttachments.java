package com.jiugeng.erp.launch;
import com.jiugeng.erp.iam.AuthenticatedActor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.*;
import java.util.*;
import static com.jiugeng.erp.launch.LaunchRules.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/launch/{kind}/{id}/attachments")
class LaunchAttachments {
 private final JdbcClient jdbc;private final LaunchWorkspace work;
 LaunchAttachments(JdbcClient jdbc,LaunchWorkspace work){this.jdbc=jdbc;this.work=work;}
 @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
 ResponseEntity<Map<String,String>> error(org.springframework.web.server.ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()));}
 @GetMapping @PreAuthorize("(#kind == 'CANDIDATE' and hasAuthority('selection.read')) or (#kind == 'DEVELOPMENT' and hasAuthority('development.read'))")
 List<Map<String,Object>> list(@PathVariable String kind,@PathVariable String id){work.get(kind,id);return jdbc.sql("SELECT id,file_name AS name,byte_size AS size,created_at AS createdAt FROM launch_attachment WHERE kind=:k AND entity_id=:id ORDER BY created_at").param("k",kind).param("id",id).query().listOfRows();}
 @PostMapping @PreAuthorize("(#kind == 'CANDIDATE' and hasAuthority('selection.write')) or (#kind == 'DEVELOPMENT' and hasAuthority('development.write'))")
 Map<String,String> upload(@PathVariable String kind,@PathVariable String id,@RequestParam MultipartFile file,@AuthenticationPrincipal AuthenticatedActor actor)throws java.io.IOException {
  work.get(kind,id);require(file.getSize()>0&&file.getSize()<=5*1024*1024,"附件大小须为1字节至5MB");byte[] data=file.getBytes();String name=file.getOriginalFilename();require(name!=null&&name.length()<=200&&!name.contains("/")&&!name.contains("\\"),"文件名无效");
  String ext=name.toLowerCase(Locale.ROOT);String type=null;
  if(ext.endsWith(".pdf")&&new String(data,0,Math.min(5,data.length),java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))type="application/pdf";
  if(ext.endsWith(".png")&&data.length>8&&data[0]==(byte)137&&data[1]==80&&data[2]==78&&data[3]==71)type="image/png";
  if((ext.endsWith(".jpg")||ext.endsWith(".jpeg"))&&data.length>3&&data[0]==(byte)255&&data[1]==(byte)216&&data[2]==(byte)255)type="image/jpeg";
  require(type!=null,"只支持内容匹配的PDF、PNG、JPEG");String aid=UUID.randomUUID().toString();
  jdbc.sql("INSERT INTO launch_attachment(id,kind,entity_id,file_name,media_type,file_data,byte_size,actor_id) VALUES(:a,:k,:id,:name,:type,:data,:size,:actor)").param("a",aid).param("k",kind).param("id",id).param("name",name).param("type",type).param("data",data).param("size",data.length).param("actor",actor.userId()).update();return Map.of("id",aid,"name",name);
 }
 @GetMapping("/{attachmentId}") @PreAuthorize("(#kind == 'CANDIDATE' and hasAuthority('selection.read')) or (#kind == 'DEVELOPMENT' and hasAuthority('development.read'))")
 ResponseEntity<byte[]> download(@PathVariable String kind,@PathVariable String id,@PathVariable String attachmentId){work.get(kind,id);return jdbc.sql("SELECT file_data,file_name,media_type FROM launch_attachment WHERE id=:a AND kind=:k AND entity_id=:id").param("a",attachmentId).param("k",kind).param("id",id).query((r,n)->ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").header("Content-Disposition",ContentDisposition.attachment().filename(r.getString(2),java.nio.charset.StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(r.getString(3))).body(r.getBytes(1))).optional().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"附件不存在"));}
}
