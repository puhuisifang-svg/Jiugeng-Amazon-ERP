package com.jiugeng.erp.dashboard;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@Profile("!mock")
@RequestMapping("/api/v1/shipping")
class ShipmentDispatchController {
    private final ShipmentDispatchService service;
    ShipmentDispatchController(ShipmentDispatchService service) { this.service=service; }
    record Confirm(@Min(0) long version) {}
    @PostMapping("/today-shipments/{id}/dispatch")
    @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    ShipmentDispatchService.Dispatch dispatch(@PathVariable String id,@Valid @RequestBody Confirm request,
        @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor) {
        return service.dispatch(id,request.version(),key,actor);
    }
    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    ShipmentDispatchService.Statistics statistics(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required=false) @Size(max=128) String keyword,
        @RequestParam(required=false) @Size(max=36) String orderId,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.statistics(from,to,keyword,orderId,page,size);
    }
}
