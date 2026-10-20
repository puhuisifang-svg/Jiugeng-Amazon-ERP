package com.jiugeng.erp.customer;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/customers/invoices")
class InvoiceController {
    private final InvoiceService service;
    InvoiceController(InvoiceService service) { this.service=service; }
    @GetMapping @PreAuthorize("hasAuthority('customers.read')")
    CustomerTypes.Page<InvoiceTypes.View> list(@RequestParam(defaultValue="") @Size(max=128) String q,
        @RequestParam(required=false) Boolean shipped, @RequestParam(required=false) Boolean invoiced,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(q,shipped,invoiced,page,size);
    }
    @GetMapping("/draft") @PreAuthorize("hasAuthority('customers.write')")
    InvoiceTypes.Draft draft(@RequestParam @NotBlank @Size(max=36) String customerProductId) {
        return service.draft(customerProductId);
    }
    @PostMapping @PreAuthorize("hasAuthority('customers.write')")
    InvoiceTypes.View create(@Valid @RequestBody InvoiceTypes.Input input,@AuthenticationPrincipal AuthenticatedActor actor) {
        return service.save(null,input,actor);
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('customers.write')")
    InvoiceTypes.View update(@PathVariable String id,@Valid @RequestBody InvoiceTypes.Input input,@AuthenticationPrincipal AuthenticatedActor actor) {
        return service.save(id,input,actor);
    }
}
