package com.jiugeng.erp.catalogdeletion;
import com.jiugeng.erp.iam.AuthenticatedActor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("!mock") @RequestMapping("/api/v1/products/deleted")
class CatalogRestoreController {
 private final CatalogRestoreService service;
 CatalogRestoreController(CatalogRestoreService service){this.service=service;}
 @GetMapping @PreAuthorize("hasAuthority('products.read') and hasAuthority('products.delete')")
 CatalogRestoreService.Page deleted(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.deleted(page,size);}
 @PostMapping("/{kind}/{id}/restore") @PreAuthorize("hasAuthority('products.write') and hasAuthority('products.delete')")
 void restore(@PathVariable String kind,@PathVariable String id,@jakarta.validation.Valid @RequestBody Restore request,@AuthenticationPrincipal AuthenticatedActor actor){service.restore(kind,id,request.version(),actor);}
 record Restore(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Min(0) Long version){}
}
