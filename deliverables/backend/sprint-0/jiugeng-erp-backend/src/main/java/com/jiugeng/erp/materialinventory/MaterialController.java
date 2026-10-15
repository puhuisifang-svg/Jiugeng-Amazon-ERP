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

    MaterialController(MaterialService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('materials.read')")
    List<MaterialView> materials() { return service.materials(); }

    @GetMapping("/{materialId}")
    @PreAuthorize("hasAuthority('materials.read')")
    MaterialView material(@PathVariable String materialId) { return service.material(materialId); }

    @PostMapping
    @PreAuthorize("hasAuthority('materials.write')")
    MaterialView create(@Valid @RequestBody MaterialRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.create(request.input(), actor);
    }

    @PutMapping("/{materialId}")
    @PreAuthorize("hasAuthority('materials.write')")
    MaterialView update(@PathVariable String materialId, @Valid @RequestBody MaterialRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.update(materialId, request.input(), actor);
    }

    @PostMapping("/{materialId}/disable")
    @PreAuthorize("hasAuthority('materials.write')")
    MaterialView disable(@PathVariable String materialId, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.disable(materialId, actor);
    }

    record MaterialRequest(@NotBlank @Size(max = 64) String materialCode,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 100) String materialType,
            @Size(max = 300) String specification,
            @NotBlank @Size(max = 32) String unit,
            @Size(max = 500) String imageUrl,
            @Size(max = 36) String imageAssetId,
            @Size(max = 1000) String remark) {
        MaterialInput input() {
            return new MaterialInput(materialCode, name, materialType, specification, unit, imageUrl,
                    imageAssetId, remark);
        }
    }
}
