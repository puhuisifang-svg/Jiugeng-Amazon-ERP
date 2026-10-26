package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.AuditTypes.AuditFilter;
import static com.jiugeng.erp.iam.AuditTypes.AuditPage;
import static com.jiugeng.erp.iam.AuditTypes.Outcome;

import java.time.Instant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@Profile("!mock")
@RequestMapping("/api/v1/audit/events")
class AuditController {
    private final AuditService service;

    AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('audit.read.all', 'audit.read.self')")
    AuditPage events(
            @AuthenticationPrincipal IamTypes.SessionPrincipal principal,
            @RequestParam(required = false) @Size(max = 128) String actorLoginName,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String category,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String action,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String resourceType,
            @RequestParam(required = false) Outcome outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        boolean readAll = principal.capabilities().contains("audit.read.all");
        var result = service.find(new AuditFilter(
                readAll ? null : principal.userId(),
                readAll ? actorLoginName : null,
                category,
                action,
                resourceType,
                outcome,
                from,
                to,
                page,
                size));
        if (principal.capabilities().contains("costs.read")) return result;
        // Role changes must not expose old cost snapshots through the generic self-audit endpoint.
        return new AuditPage(result.items().stream().map(e -> "PRODUCT_COST".equals(e.resourceType())
                ? new AuditTypes.AuditEvent(e.id(), e.category(), e.action(), e.actorUserId(), e.actorLoginName(),
                    e.actorDisplayName(), e.resourceType(), e.resourceId(), e.outcome(), null, null,
                    "成本操作（当前角色无成本查看权限）", e.occurredAt()) : e).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
    }
}
