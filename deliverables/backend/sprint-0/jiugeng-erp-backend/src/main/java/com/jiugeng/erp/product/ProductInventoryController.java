package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/products/inventory")
class ProductInventoryController {
    private final ProductInventoryService service;
    ProductInventoryController(ProductInventoryService service) { this.service=service; }
    @GetMapping("/balances") @PreAuthorize("hasAuthority('products.read') and hasAuthority('inventory.read')")
    List<ProductInventoryService.Balance> balances(@RequestParam List<String> variantIds) { return service.balances(variantIds); }
    @GetMapping("/{variantId}/transactions") @PreAuthorize("hasAuthority('products.read') and hasAuthority('inventory.read')")
    ProductInventoryService.History history(@PathVariable String variantId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.history(variantId,page,size); }
    @PostMapping("/{variantId}/transactions")
    @PreAuthorize("hasAuthority('products.read') and ((#request.type() == 'INBOUND' or #request.type() == 'ADJUST_IN') and hasAuthority('inventory.inbound') or (#request.type() == 'OUTBOUND' or #request.type() == 'ADJUST_OUT') and hasAuthority('inventory.outbound'))")
    ProductInventoryService.Result transact(@PathVariable String variantId,@Valid @RequestBody Command request,@AuthenticationPrincipal AuthenticatedActor actor) {
        return service.transact(variantId,request.requestId(),request.type(),request.quantity(),request.reason(),actor);
    }
    record Command(@NotBlank @Size(max=100) String requestId,@NotBlank String type,@NotNull @Digits(integer=15,fraction=0) BigDecimal quantity,@NotBlank @Size(max=500) String reason) {}
}
