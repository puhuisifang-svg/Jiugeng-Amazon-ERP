package com.jiugeng.erp.development;

import static com.jiugeng.erp.development.DevelopmentTypes.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/product-development")
class DevelopmentController {
    private final DevelopmentService service;
    DevelopmentController(DevelopmentService service) { this.service = service; }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    org.springframework.http.ResponseEntity<java.util.Map<String,String>> error(org.springframework.web.server.ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode())
                .body(java.util.Map.of("code","DEVELOPMENT_REQUEST_FAILED","message",error.getReason()));
    }
    @GetMapping @PreAuthorize("hasAuthority('products.read')")
    Page list(@RequestParam(required=false) Status status, @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return service.list(status,q,page,size);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('products.read')")
    Detail detail(@PathVariable String id) { return service.detail(id); }
    @PostMapping @PreAuthorize("hasAuthority('products.write')")
    View create(@RequestBody Create input,@AuthenticationPrincipal AuthenticatedActor actor) {
        return service.create(input,actor);
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('products.write')")
    View update(@PathVariable String id,@RequestBody Update input,@AuthenticationPrincipal AuthenticatedActor actor) {
        return service.update(id,input,actor);
    }
    @PostMapping("/{id}/transition") @PreAuthorize("hasAuthority('products.write')")
    View transition(@PathVariable String id,@RequestBody Transition input,@AuthenticationPrincipal AuthenticatedActor actor) {
        return service.transition(id,input,actor);
    }
}
