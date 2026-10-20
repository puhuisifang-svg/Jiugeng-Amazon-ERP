package com.jiugeng.erp.customer;

import static com.jiugeng.erp.customer.CustomerTypes.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/customers")
class CustomerController {
    private final CustomerService service;
    CustomerController(CustomerService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('customers.read')")
    Page<CustomerView> customers(@RequestParam(defaultValue="") @Size(max=128) String q,
        @RequestParam(required=false) Status status, @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.customers(q,status,page,size); }
    @GetMapping("/default") @PreAuthorize("hasAuthority('customers.read')")
    CustomerView defaultCustomer() { return service.defaultCustomer(); }
    @PostMapping @PreAuthorize("hasAuthority('customers.write')")
    CustomerView create(@Valid @RequestBody CustomerInput input, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.saveCustomer(null,input,actor);
    }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('customers.write')")
    CustomerView update(@PathVariable String id, @Valid @RequestBody CustomerInput input, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.saveCustomer(id,input,actor);
    }
    @GetMapping("/products") @PreAuthorize("hasAuthority('customers.read')")
    Page<ProductView> products(@RequestParam(defaultValue="") @Size(max=128) String q,
        @RequestParam(defaultValue="") @Size(max=36) String customerId, @RequestParam(required=false) Status status,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.products(q,customerId,status,page,size); }
    @PostMapping("/products") @PreAuthorize("hasAuthority('customers.write')")
    ProductView createProduct(@Valid @RequestBody ProductInput input, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.saveProduct(null,input,actor);
    }
    @PutMapping("/products/{id}") @PreAuthorize("hasAuthority('customers.write')")
    ProductView updateProduct(@PathVariable String id, @Valid @RequestBody ProductInput input, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.saveProduct(id,input,actor);
    }
}
