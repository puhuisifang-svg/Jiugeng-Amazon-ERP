package com.jiugeng.erp.importexport;

import static com.jiugeng.erp.importexport.ImportTypes.*;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1")
class ImportController {
    private final ImportService service; private final ImportTemplateRegistry templates;
    ImportController(ImportService service,ImportTemplateRegistry templates){this.service=service;this.templates=templates;}

    @GetMapping("/import-templates/{type}")
    @PreAuthorize("hasAuthority('imports.template.download')")
    ResponseEntity<byte[]> template(@PathVariable ImportType type){
        var definition=templates.definition(type); return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ImportService.XLSX_MIME))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(definition.fileName()).build().toString())
                .body(templates.bytes(type));
    }
    @PostMapping(value="/import-batches",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('imports.validate')")
    ResponseEntity<ImportService.UploadReceipt> upload(@RequestParam ImportType importType,
            @RequestParam String templateVersion,@RequestParam MultipartFile file,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal AuthenticatedActor actor) throws IOException {
        return ResponseEntity.accepted().body(service.upload(importType,templateVersion,file.getOriginalFilename(),
                file.getContentType(),file.getBytes(),idempotencyKey,actor));
    }
    @GetMapping("/import-batches") @PreAuthorize("hasAuthority('imports.read')")
    List<BatchView> list(){return service.list();}
    @GetMapping("/import-batches/{id}") @PreAuthorize("hasAuthority('imports.read')")
    BatchView get(@PathVariable String id){return service.get(id);}
    @GetMapping("/import-batches/{id}/issues") @PreAuthorize("hasAuthority('imports.read')")
    List<IssueView> issues(@PathVariable String id){return service.issues(id);}
    @GetMapping("/import-batches/{id}/preview") @PreAuthorize("hasAuthority('imports.read')")
    PreviewView preview(@PathVariable String id){return service.preview(id);}
    @PostMapping("/import-batches/{id}/cancel") @PreAuthorize("hasAuthority('imports.cancel')")
    BatchView cancel(@PathVariable String id,@RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal AuthenticatedActor actor){return service.cancel(id,key,actor);}
    @PostMapping("/import-batches/{id}/revalidate") @PreAuthorize("hasAuthority('imports.validate')")
    ResponseEntity<ImportService.UploadReceipt> revalidate(@PathVariable String id,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return ResponseEntity.accepted().body(service.revalidate(id,key,actor));
    }
    @PostMapping("/import-batches/{id}/commit") @PreAuthorize("hasAuthority('imports.commit')")
    ResponseEntity<BatchView> commit(@PathVariable String id,@Valid @RequestBody CommitRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return ResponseEntity.accepted().body(service.commit(id,request.previewVersion(),request.fileHash(),
                request.warningsAccepted(),key,actor));
    }
    @GetMapping("/import-batches/{id}/result") @PreAuthorize("hasAuthority('imports.read')")
    Map<String,Object> result(@PathVariable String id){return service.result(id);}
    @GetMapping("/import-batches/{id}/result-file") @PreAuthorize("hasAuthority('imports.result.download')")
    ResponseEntity<Void> resultFile(@PathVariable String id){service.result(id);throw new ImportException(409,
            "IMPORT_RESULT_FILE_NOT_AVAILABLE","当前批次没有结果文件");}
    record CommitRequest(@NotBlank String previewVersion,@NotBlank String fileHash,boolean warningsAccepted){}
}
