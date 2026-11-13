package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.MaterialTypes.MaterialInput;
import static com.jiugeng.erp.materialinventory.MaterialTypes.MaterialView;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/materials")
class MaterialController {
    private final MaterialService service;
    private final CostService costs;

    MaterialController(MaterialService service,CostService costs) { this.service = service;this.costs=costs; }

    @GetMapping("/unit-options")
    @PreAuthorize("hasAuthority('materials.read')")
    List<String> unitOptions() { return service.unitOptions(); }

    @PostMapping("/unit-options/remove")
    @PreAuthorize("hasAuthority('materials.write')")
    void removeUnit(@Valid @RequestBody UnitOptionRequest request, @AuthenticationPrincipal AuthenticatedActor actor) {
        service.hideUnit(request.name(),actor);
    }
    record UnitOptionRequest(@NotBlank @Size(max=32) String name) {}

    @GetMapping("/type-options")
    @PreAuthorize("hasAuthority('materials.read')")
    List<String> typeOptions() { return service.typeOptions(); }

    @PostMapping("/type-options/remove")
    @PreAuthorize("hasAuthority('materials.write')")
    void removeType(@Valid @RequestBody TypeOptionRequest request, @AuthenticationPrincipal AuthenticatedActor actor) {
        service.hideType(request.name(),actor);
    }
    record TypeOptionRequest(@NotBlank @Size(max=100) String name) {}

    @GetMapping
    @PreAuthorize("hasAuthority('materials.read')")
    List<MaterialView> materials() { return service.materials(); }

    @GetMapping("/{materialId}")
    @PreAuthorize("hasAuthority('materials.read')")
    MaterialView material(@PathVariable String materialId) { return service.material(materialId); }

    @PostMapping("/batch-delete")
    @PreAuthorize("hasAuthority('materials.write')")
    void delete(@Valid @RequestBody DeleteRequest request, @AuthenticationPrincipal AuthenticatedActor actor) {
        service.delete(request.ids(), actor);
    }

    record DeleteRequest(@jakarta.validation.constraints.NotEmpty @Size(max = 100)
            List<@NotBlank @Size(max = 36) String> ids) {}

    @PostMapping
    @PreAuthorize("hasAuthority('materials.write') and (#request.costPrice == null or (hasAuthority('costs.write') and hasAuthority('costs.read')))")
    @org.springframework.transaction.annotation.Transactional
    MaterialView create(@Valid @RequestBody MaterialRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        var result=service.create(request.input(), actor);
        if(request.costPrice()!=null)costs.savePrice(result.id(),request.costPrice(),actor);
        return result;
    }

    @PutMapping("/{materialId}")
    @PreAuthorize("hasAuthority('materials.write') and (#request.costPrice == null or (hasAuthority('costs.write') and hasAuthority('costs.read')))")
    @org.springframework.transaction.annotation.Transactional
    MaterialView update(@PathVariable String materialId, @Valid @RequestBody MaterialRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        var result=service.update(materialId, request.input(), actor);
        if(request.costPrice()!=null)costs.savePrice(result.id(),request.costPrice(),actor);
        return result;
    }

    @PostMapping("/{materialId}/disable")
    @PreAuthorize("hasAuthority('materials.write')")
    MaterialView disable(@PathVariable String materialId, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.disable(materialId, actor);
    }

    @PostMapping("/{materialId}/enable")
    @PreAuthorize("hasAuthority('materials.write')")
    MaterialView enable(@PathVariable String materialId, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.enable(materialId, actor);
    }

    record MaterialRequest(@Size(max = 64) String materialCode,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String materialType,
            @Size(max = 300) String specification,
            @NotBlank @Size(max = 32) String unit,
            @Size(max = 500) String imageUrl,
            @Size(max = 36) String imageAssetId,
            @Size(max = 1000) String remark, @Valid CostTypes.PriceInput costPrice) {
        MaterialInput input() {
            return new MaterialInput(materialCode, name, materialType, specification, unit, imageUrl,
                    imageAssetId, remark);
        }
    }
}
