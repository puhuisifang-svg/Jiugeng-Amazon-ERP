package com.jiugeng.erp.forwarder;
import java.util.*;import java.nio.charset.StandardCharsets;
import com.jiugeng.erp.iam.AuthenticatedActor;
import org.springframework.context.annotation.Profile;import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;
@RestController @Profile("!mock") @RequestMapping("/api/v1/forwarder-documents")
class ForwarderController{
 private final ForwarderDocuments service;private final ForwarderWorkbook workbook;
 ForwarderController(ForwarderDocuments service,ForwarderWorkbook workbook){this.service=service;this.workbook=workbook;}
 @GetMapping @PreAuthorize("hasAuthority('forwarders.read')") ForwarderDocuments.Page list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="")String carrier,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(q,carrier,page,size);}
 @GetMapping("/options") @PreAuthorize("hasAuthority('forwarders.read')") Map<String,Object> options(@RequestParam String carrier){return workbook.options(carrier);}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('forwarders.read')") ForwarderDocuments.Document get(@PathVariable String id){return service.get(id);}
 @PostMapping @PreAuthorize("hasAuthority('forwarders.write')") ForwarderDocuments.Document create(@RequestBody ForwarderDocuments.Input in,@AuthenticationPrincipal AuthenticatedActor actor){return service.save(null,in,actor);}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('forwarders.write')") ForwarderDocuments.Document update(@PathVariable String id,@RequestBody ForwarderDocuments.Input in,@AuthenticationPrincipal AuthenticatedActor actor){return service.save(id,in,actor);}
 @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('forwarders.delete')") ResponseEntity<Void> delete(@PathVariable String id,@RequestParam long version,@AuthenticationPrincipal AuthenticatedActor actor){service.delete(id,version,actor);return ResponseEntity.noContent().build();}
 @GetMapping("/{id}/export") @PreAuthorize("hasAuthority('forwarders.export')") ResponseEntity<byte[]> export(@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor actor){var d=service.get(id);byte[] bytes=workbook.export(d);service.record(actor,"FORWARDER_DOCUMENT_EXPORT",d);boolean k=d.carrier().equals("KAIQI");return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename((k?"凯琦_":"华茂万通_")+d.documentNo().replaceAll("[\\\\/:*?\"<>|\\r\\n]","_")+(k?".xls":".xlsx"),StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType(k?"application/vnd.ms-excel":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);}
 @ExceptionHandler(ResponseStatusException.class)ResponseEntity<Map<String,String>> error(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"操作失败")));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","单据编号已存在，请更换编号"));}
}
