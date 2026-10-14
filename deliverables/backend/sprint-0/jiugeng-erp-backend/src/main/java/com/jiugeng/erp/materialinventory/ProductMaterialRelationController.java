package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.ProductMaterialRelationTypes.ApplyAllInput;
import static com.jiugeng.erp.materialinventory.ProductMaterialRelationTypes.RelationInput;
import static com.jiugeng.erp.materialinventory.ProductMaterialRelationTypes.RelationView;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/product-material-relations")
class ProductMaterialRelationController {
    private final ProductMaterialRelationService service;

    ProductMaterialRelationController(ProductMaterialRelationService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('products.read') and hasAuthority('materials.read')")
    List<RelationView> relations(@RequestParam(required = false) String productId,
            @RequestParam(required = false) String variantId) {
        return service.relations(productId, variantId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('products.write') and hasAuthority('materials.write')")
    RelationView create(@Valid @RequestBody RelationRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.create(request.input(), actor);
    }

    @PutMapping("/{relationId}")
    @PreAuthorize("hasAuthority('products.write') and hasAuthority('materials.write')")
    RelationView update(@PathVariable String relationId, @Valid @RequestBody RelationRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.update(relationId, request.input(), actor);
    }

    @PostMapping("/{relationId}/disable")
    @PreAuthorize("hasAuthority('products.write') and hasAuthority('materials.write')")
    RelationView disable(@PathVariable String relationId, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.disable(relationId, actor);
    }

    @PostMapping("/apply-to-all")
    @PreAuthorize("hasAuthority('products.write') and hasAuthority('materials.write')")
    List<RelationView> applyToAll(@Valid @RequestBody ApplyAllRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.applyToAll(request.input(), actor);
    }

    record RelationRequest(@NotBlank @Size(max = 36) String productId,
            @NotBlank @Size(max = 36) String variantId,
            @NotBlank @Size(max = 36) String materialId,
            @NotNull @Positive BigDecimal standardQuantity,
            @NotBlank @Size(max = 32) String unit,
            @DecimalMin("0") @DecimalMax("1") BigDecimal lossRate,
            @Size(max = 1000) String remark,
            Boolean enabled) {
        RelationInput input() { return new RelationInput(productId, variantId, materialId, standardQuantity,
                unit, lossRate, remark, enabled); }
    }

    record ApplyAllRequest(@NotBlank @Size(max = 36) String productId,
            @NotBlank @Size(max = 36) String materialId,
            @NotNull @Positive BigDecimal standardQuantity,
            @NotBlank @Size(max = 32) String unit,
            @DecimalMin("0") @DecimalMax("1") BigDecimal lossRate,
            @Size(max = 1000) String remark) {
        ApplyAllInput input() { return new ApplyAllInput(productId, materialId, standardQuantity, unit, lossRate, remark); }
    }
}
