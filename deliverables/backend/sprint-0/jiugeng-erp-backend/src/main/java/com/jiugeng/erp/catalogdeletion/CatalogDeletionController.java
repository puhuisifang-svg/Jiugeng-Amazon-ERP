package com.jiugeng.erp.catalogdeletion;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock")
class CatalogDeletionController {
    private final CatalogDeletionService service;
    CatalogDeletionController(CatalogDeletionService service) { this.service=service; }
    @DeleteMapping("/api/v1/products/{id}") @PreAuthorize("hasAuthority('products.write')")
    ResponseEntity<Void> product(@PathVariable String id,@RequestParam @Min(0) long version,@AuthenticationPrincipal AuthenticatedActor actor) {
        service.product(id,null,version,null,actor);return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/api/v1/products/{id}/skus/{variantId}") @PreAuthorize("hasAuthority('products.write')")
    ResponseEntity<Void> sku(@PathVariable String id,@PathVariable String variantId,@RequestParam @Min(0) long productVersion,
        @RequestParam @Min(0) long version,@AuthenticationPrincipal AuthenticatedActor actor) {
        service.product(id,variantId,productVersion,version,actor);return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/api/v1/customers/products/{id}") @PreAuthorize("hasAuthority('customers.write')")
    ResponseEntity<Void> customerProduct(@PathVariable String id,@RequestParam @Min(0) long version,@AuthenticationPrincipal AuthenticatedActor actor) {
        service.customerProduct(id,version,actor);return ResponseEntity.noContent().build();
    }
}
