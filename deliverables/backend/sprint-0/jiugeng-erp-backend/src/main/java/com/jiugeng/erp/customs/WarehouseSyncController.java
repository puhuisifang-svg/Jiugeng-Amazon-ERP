package com.jiugeng.erp.customs;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!mock") @RequestMapping("/api/v1/customs-warehouses/sync")
class WarehouseSyncController {
 private final WarehouseSync sync;
 WarehouseSyncController(WarehouseSync sync){this.sync=sync;}
 @GetMapping @PreAuthorize("hasAuthority('products.read')") WarehouseSync.Status status(){return sync.status();}
 @PostMapping @PreAuthorize("hasAuthority('products.write')") WarehouseSync.Status start(){return sync.start(false);}
}
