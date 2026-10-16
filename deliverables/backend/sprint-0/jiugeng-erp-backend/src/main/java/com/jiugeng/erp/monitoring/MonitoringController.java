package com.jiugeng.erp.monitoring;

import static com.jiugeng.erp.monitoring.MonitoringTypes.*;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/product-monitoring")
@PreAuthorize("isAuthenticated()")
class MonitoringController {
    private final MonitoringService service;
    MonitoringController(MonitoringService service) { this.service = service; }

    @GetMapping
    Page<Target> list(@RequestParam(defaultValue="") String q, @RequestParam(defaultValue="ASIN") String searchBy,
                      @RequestParam(defaultValue="ALL") String status, @RequestParam(defaultValue="") String owner,
                      @RequestParam(defaultValue="") String tag, @RequestParam(defaultValue="false") boolean followed,
                      @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
                      @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.list(q, searchBy, status, owner, tag, followed, page, size, actor.userId());
    }
    @PostMapping List<RowResult> create(@RequestBody CreateInput input, @AuthenticationPrincipal AuthenticatedActor actor) { return service.create(input, actor); }
    @PostMapping("/batch") List<RowResult> batch(@RequestBody BatchInput input, @AuthenticationPrincipal AuthenticatedActor actor) { return service.batch(input, actor); }
    @GetMapping("/preferences") Preferences preferences(@AuthenticationPrincipal AuthenticatedActor actor) { return service.preferences(actor.userId()); }
    @PutMapping("/preferences") Preferences preferences(@RequestBody ColumnInput input, @AuthenticationPrincipal AuthenticatedActor actor) { return service.columns(actor.userId(), input.columns()); }
    @GetMapping("/{id}") Detail detail(@PathVariable String id, @AuthenticationPrincipal AuthenticatedActor actor) { return service.detail(id, actor.userId()); }
    @PutMapping("/{id}/follow") Preferences follow(@PathVariable String id, @RequestBody FollowInput input, @AuthenticationPrincipal AuthenticatedActor actor) { return service.follow(id, actor.userId(), input.followed()); }
    @GetMapping("/{id}/snapshots") Page<Snapshot> snapshots(@PathVariable String id, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) { return service.history(id, page, size); }
    @PostMapping("/{id}/snapshots") Snapshot snapshot(@PathVariable String id, @RequestBody SnapshotInput input) { return service.snapshot(id, input); }
    record FollowInput(boolean followed) {}
    record ColumnInput(List<String> columns) {}

    @ExceptionHandler(MonitoringException.class)
    ResponseEntity<Map<String,String>> error(MonitoringException error) {
        return ResponseEntity.status(error.status).body(Map.of("code", "MONITORING_" + error.status, "message", error.getMessage()));
    }
}
