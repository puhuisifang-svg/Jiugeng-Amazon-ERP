package com.jiugeng.erp.hs;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.context.annotation.Profile;
@RestController @Profile("!mock") @RequestMapping("/api/v1/hs-declaration-schemas")
class HsSchemaController {
 private final HsSchemaService service;
 HsSchemaController(HsSchemaService service){this.service=service;}
 @GetMapping("/{code}") @PreAuthorize("hasAuthority('products.read')")
 HsSchemaService.Schema get(@PathVariable String code){return service.lookup(code);}
}
