package com.jiugeng.erp.watch;
import static com.jiugeng.erp.watch.WatchTypes.*;import com.jiugeng.erp.iam.AuthenticatedActor;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.*;import org.springframework.context.annotation.Profile;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @Profile("!mock") @RequestMapping("/api/v1/watch") class WatchController{
 private final WatchService service;WatchController(WatchService service){this.service=service;}
 @GetMapping("/stores") @PreAuthorize("hasAuthority('watch.read')") List<StoreView> stores(){return service.stores();}
 @PostMapping("/stores") @PreAuthorize("hasAuthority('watch.write')") StoreView create(@Valid @RequestBody StoreRequest r,@AuthenticationPrincipal AuthenticatedActor a){return service.createStore(r.storeKey(),r.storeName(),r.sourceUrl(),a);}
 @PostMapping("/stores/{id}/runs") @PreAuthorize("hasAuthority('watch.write')") RunResult run(@PathVariable String id,@Valid @RequestBody RunRequest r,@AuthenticationPrincipal AuthenticatedActor a){return service.run(id,r.requestId(),r.status(),r.itemCount(),r.pageCount(),r.pagesComplete(),r.asins(),a);}
 @GetMapping("/events") @PreAuthorize("hasAuthority('watch.read')") List<EventView> events(){return service.events();}
 @PostMapping("/events/{id}/confirm") @PreAuthorize("hasAuthority('watch.write')") Confirmation confirm(@PathVariable String id,@AuthenticationPrincipal AuthenticatedActor a){return service.confirm(id,a);}
 record StoreRequest(@NotBlank @Size(max=100) String storeKey,@NotBlank @Size(max=200) String storeName,@NotBlank @Size(max=1000) String sourceUrl){}
 record RunRequest(@NotBlank @Size(max=100) String requestId,@NotNull RunStatus status,@PositiveOrZero int itemCount,@PositiveOrZero int pageCount,boolean pagesComplete,@NotNull @Size(max=1000) List<@NotBlank String> asins){}
}
