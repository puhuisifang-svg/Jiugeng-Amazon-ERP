package com.jiugeng.erp.dashboard;

import com.jiugeng.erp.customer.OrderFulfillment;
import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("!mock") @RequestMapping("/api/v1/customer-orders")
class OrderShipmentController {
    private final OrderShipmentService shipments;private final OrderFulfillment orders;
    OrderShipmentController(OrderShipmentService shipments,OrderFulfillment orders) {this.shipments=shipments;this.orders=orders;}
    @PostMapping("/{id}/arrangements") @PreAuthorize("hasAuthority('customers.write') and hasAuthority('dashboard.shipments.manage')")
    java.util.List<OrderFulfillment.Allocation> arrange(@PathVariable String id,@Valid @RequestBody OrderFulfillment.Request input,@RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor) {return shipments.arrange(id,input,key,actor);}
    @GetMapping("/{id}/arrangements") @PreAuthorize("hasAuthority('customers.read')")
    java.util.List<OrderFulfillment.Allocation> list(@PathVariable String id) {return orders.allocations(id);}
    record Logistics(@NotNull Long version,@Size(max=1000) String logistics) {}
    @PutMapping("/arrangements/{id}/logistics") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    void logistics(@PathVariable String id,@Valid @RequestBody Logistics input,@AuthenticationPrincipal AuthenticatedActor actor) {orders.logistics(id,input.version(),input.logistics(),actor);}
}
