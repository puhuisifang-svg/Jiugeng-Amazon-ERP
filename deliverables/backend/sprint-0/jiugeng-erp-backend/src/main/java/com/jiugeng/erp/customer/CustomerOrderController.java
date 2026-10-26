package com.jiugeng.erp.customer;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditHistoryLookup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/customer-orders")
class CustomerOrderController {
    private final CustomerOrderService service; private final AuditHistoryLookup history;
    CustomerOrderController(CustomerOrderService service,AuditHistoryLookup history) {this.service=service;this.history=history;}
    @GetMapping @PreAuthorize("hasAuthority('customers.read')")
    CustomerTypes.Page<CustomerOrderTypes.View> list(@RequestParam(defaultValue="") @Size(max=128) String q,
        @RequestParam(defaultValue="") String status,@RequestParam(defaultValue="") String customerId,
        @RequestParam(defaultValue="") String source,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
        @RequestParam(required=false) LocalDate deliveryFrom,@RequestParam(required=false) LocalDate deliveryTo,
        @RequestParam(defaultValue="") String shipping,@RequestParam(defaultValue="") String invoiceStatus,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.list(q,status,customerId,source,from,to,deliveryFrom,deliveryTo,shipping,invoiceStatus,page,size);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('customers.read')") CustomerOrderTypes.View get(@PathVariable String id) {return service.get(id);}
    @PostMapping @PreAuthorize("hasAuthority('customers.write')") CustomerOrderTypes.View create(@Valid @RequestBody CustomerOrderTypes.Input input,@RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor) {return service.save(null,input,actor,key);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('customers.write')") CustomerOrderTypes.View update(@PathVariable String id,@Valid @RequestBody CustomerOrderTypes.Input input,@AuthenticationPrincipal AuthenticatedActor actor) {return service.save(id,input,actor,null);}
    @PostMapping("/{id}/transition") @PreAuthorize("hasAuthority('customers.write')") CustomerOrderTypes.View transition(@PathVariable String id,@Valid @RequestBody CustomerOrderTypes.Transition input,@AuthenticationPrincipal AuthenticatedActor actor) {return service.transition(id,input,actor);}
    @GetMapping("/{id}/history") @PreAuthorize("hasAuthority('customers.read')") java.util.List<AuditHistoryLookup.HistoryEvent> history(@PathVariable String id) {service.get(id);return history.find("CUSTOMER_ORDER",id);}
    @PostMapping("/{id}/invoice") @PreAuthorize("hasAuthority('customers.write')") CustomerOrderTypes.Invoice generate(@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor actor) {return service.generateInvoice(id,actor);}
    @GetMapping("/invoices") @PreAuthorize("hasAuthority('customers.read')") CustomerTypes.Page<CustomerOrderTypes.Invoice> invoices(@RequestParam(defaultValue="") @Size(max=128) String q,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {return service.invoices(q,page,size);}
    @GetMapping("/invoices/{id}") @PreAuthorize("hasAuthority('customers.read')") CustomerOrderTypes.Invoice invoice(@PathVariable String id) {return service.invoice(id);}
    @PutMapping("/invoices/{id}") @PreAuthorize("hasAuthority('customers.write')") CustomerOrderTypes.Invoice invoiceUpdate(@PathVariable String id,@Valid @RequestBody CustomerOrderTypes.InvoiceUpdate input,@AuthenticationPrincipal AuthenticatedActor actor) {return service.updateInvoice(id,input,actor);}
}
