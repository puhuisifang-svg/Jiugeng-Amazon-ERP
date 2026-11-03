package com.jiugeng.erp.customs;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @Profile("!mock") @RequestMapping("/api/v1/customs-documents")
class CustomsDocumentController {
 private final CustomsDocuments service;private final CustomsWorkbook workbook;
 CustomsDocumentController(CustomsDocuments service,CustomsWorkbook workbook){this.service=service;this.workbook=workbook;}
 @GetMapping @PreAuthorize("hasAuthority('products.read')") CustomsDocuments.Page list(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.list(q,status,page,size);}
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('products.read')") CustomsDocuments.Document get(@PathVariable String id){return service.get(id);}
 @PostMapping @PreAuthorize("hasAuthority('products.write')") CustomsDocuments.Document create(@RequestBody CustomsDocuments.Input in,@AuthenticationPrincipal AuthenticatedActor actor){return service.save(null,in,actor);}
 @PutMapping("/{id}") @PreAuthorize("hasAuthority('products.write')") CustomsDocuments.Document update(@PathVariable String id,@RequestBody CustomsDocuments.Input in,@AuthenticationPrincipal AuthenticatedActor actor){return service.save(id,in,actor);}
 @PostMapping("/{id}/void") @PreAuthorize("hasAuthority('products.write')") CustomsDocuments.Document cancel(@PathVariable String id,@RequestParam long version,@AuthenticationPrincipal AuthenticatedActor actor){return service.voidDocument(id,version,actor);}
 @GetMapping("/{id}/export") @PreAuthorize("hasAuthority('products.export')") @Transactional ResponseEntity<byte[]> export(@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor actor){var d=service.get(id);CustomsDocuments.require(!d.status().equals("VOID"),"已作废单据不可导出");byte[] bytes=workbook.export(d);service.record(actor,"CUSTOMS_DOCUMENT_EXPORT",d);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("报关资料_"+d.documentNo().replaceAll("[\\\\/:*?\"<>|\\r\\n]","_")+".xlsx",StandardCharsets.UTF_8).build().toString()).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(bytes);}
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> invalid(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("detail",Objects.requireNonNullElse(e.getReason(),"操作失败")));}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) ResponseEntity<Map<String,String>> duplicate(){return ResponseEntity.status(409).body(Map.of("detail","单据编号已存在，请换一个编号"));}
}
