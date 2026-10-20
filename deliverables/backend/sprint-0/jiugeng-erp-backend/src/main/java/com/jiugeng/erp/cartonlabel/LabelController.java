package com.jiugeng.erp.cartonlabel;

import static com.jiugeng.erp.cartonlabel.LabelTypes.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/carton-labels")
class LabelController {
    private final LabelService service;
    LabelController(LabelService service) {this.service=service;}
    @GetMapping("/customers/{id}/templates") @PreAuthorize("hasAuthority('customers.read')")
    List<Template> templates(@PathVariable String id){return service.templates(id);}
    @PostMapping("/customers/{id}/templates") @PreAuthorize("hasAuthority('customers.write')")
    Template create(@PathVariable String id,@Valid @RequestBody TemplateInput input,@AuthenticationPrincipal AuthenticatedActor actor){return service.save(id,null,input,actor);}
    @PutMapping("/customers/{id}/templates/{template}") @PreAuthorize("hasAuthority('customers.write')")
    Template update(@PathVariable String id,@PathVariable String template,@Valid @RequestBody TemplateInput input,@AuthenticationPrincipal AuthenticatedActor actor){return service.save(id,template,input,actor);}
    @PostMapping("/template-preview") @PreAuthorize("hasAuthority('customers.write')")
    ResponseEntity<byte[]> templatePreview(@Valid @RequestBody Config config){return pdf(service.templatePreview(config),"template-preview.pdf");}
    @GetMapping("/shipments/{id}/draft") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    Draft draft(@PathVariable String id){return service.draft(id);}
    @GetMapping("/shipments/{id}/history") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    List<Export> history(@PathVariable String id){return service.history(id);}
    @PostMapping("/preview") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    ResponseEntity<byte[]> preview(@Valid @RequestBody Batch input){return pdf(service.preview(input),"carton-preview.pdf");}
    @PostMapping("/exports") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    Export generate(@Valid @RequestBody Batch input,@AuthenticationPrincipal AuthenticatedActor actor){return service.generate(input,actor);}
    @GetMapping("/exports/{id}/pdf") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    ResponseEntity<byte[]> download(@PathVariable String id){return pdf(service.download(id),"carton-labels.pdf");}
    private ResponseEntity<byte[]> pdf(byte[] bytes,String filename) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+filename+"\"").body(bytes);
    }
}
