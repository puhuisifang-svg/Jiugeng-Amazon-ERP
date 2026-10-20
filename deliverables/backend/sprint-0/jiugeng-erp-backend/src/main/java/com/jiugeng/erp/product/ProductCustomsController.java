package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/product-customs")
class ProductCustomsController {
    private final ProductCustomsService service;
    ProductCustomsController(ProductCustomsService service) { this.service=service; }
    @GetMapping @PreAuthorize("hasAuthority('products.read')")
    ProductCustomsService.Page list(@RequestParam(defaultValue="") @Size(max=128) String q,
        @RequestParam(defaultValue="") @Pattern(regexp="^$|MISSING|DRAFT|REVIEWED") String state,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.list(q,state,page,size); }
    @PutMapping("/{variantId}") @PreAuthorize("hasAuthority('products.write')")
    CustomsProfile save(@PathVariable String variantId,@Valid @RequestBody CustomsProfile input,@AuthenticationPrincipal AuthenticatedActor actor) {
        return service.save(variantId,input,actor);
    }
}
