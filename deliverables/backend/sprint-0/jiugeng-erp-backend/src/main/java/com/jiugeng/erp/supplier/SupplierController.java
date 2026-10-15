package com.jiugeng.erp.supplier;
import static com.jiugeng.erp.supplier.SupplierTypes.*;import com.jiugeng.erp.iam.AuthenticatedActor;import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.List;import org.springframework.context.annotation.Profile;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.security.core.annotation.AuthenticationPrincipal;import org.springframework.web.bind.annotation.*;
@RestController @Profile("!mock") @RequestMapping("/api/v1/suppliers") class SupplierController{private final SupplierService service;SupplierController(SupplierService service){this.service=service;}
@GetMapping @PreAuthorize("hasAuthority('suppliers.read')") Page<View> list(@RequestParam(required=false)@Size(max=128)String q,@RequestParam(required=false)Status status,@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size){return service.list(q,status,page,size);}
@GetMapping("/{id}") @PreAuthorize("hasAuthority('suppliers.read')") View get(@PathVariable String id){return service.get(id);}
@PostMapping @PreAuthorize("hasAuthority('suppliers.write')") View create(@Valid@RequestBody Request r,@AuthenticationPrincipal AuthenticatedActor a){return service.create(r.input(),a);}
@PutMapping("/{id}") @PreAuthorize("hasAuthority('suppliers.write')") View update(@PathVariable String id,@Valid@RequestBody Request r,@AuthenticationPrincipal AuthenticatedActor a){return service.update(id,r.input(),a);}
@PostMapping("/{id}/disable") @PreAuthorize("hasAuthority('suppliers.write')") View disable(@PathVariable String id,@Valid@RequestBody VersionRequest r,@AuthenticationPrincipal AuthenticatedActor a){return service.disable(id,r.version(),a);}
@GetMapping("/variant/{id}") @PreAuthorize("hasAuthority('suppliers.read')") List<LinkView> variantLinks(@PathVariable String id){return service.variantLinks(id);}
@GetMapping("/material/{id}") @PreAuthorize("hasAuthority('suppliers.read')") List<LinkView> materialLinks(@PathVariable String id){return service.materialLinks(id);}
@PutMapping("/material/{id}") @PreAuthorize("hasAuthority('suppliers.write') and hasAuthority('materials.write')") List<LinkView> replaceMaterial(@PathVariable String id,@Valid@RequestBody LinkRequest r,@AuthenticationPrincipal AuthenticatedActor a){service.replaceMaterialLinks(id,r.links().stream().map(Link::input).toList(),a);return service.materialLinks(id);}
record Request(@NotBlank@Size(max=128)String name,@Size(max=64)String contactPerson,@Size(max=32)String phone,@Size(max=500)String sourceUrl,@Size(max=36)String qrImageId,@Size(max=500)String remark,Status status,Long version){Input input(){return new Input(name,contactPerson,phone,sourceUrl,qrImageId,remark,status,version);}}
record VersionRequest(@Min(0)long version){}
record LinkRequest(@NotNull@Size(max=50)List<@Valid Link> links){}
record Link(@NotBlank@Size(max=36)String supplierId,@Size(max=500)String purchaseUrl,@Size(max=500)String remark,boolean primary){LinkInput input(){return new LinkInput(supplierId,purchaseUrl,remark,primary);}}
}
