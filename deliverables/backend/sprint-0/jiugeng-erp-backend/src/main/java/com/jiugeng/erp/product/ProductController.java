package com.jiugeng.erp.product;

import static com.jiugeng.erp.product.ProductTypes.AttributeInput;
import static com.jiugeng.erp.product.ProductTypes.ProductInput;
import static com.jiugeng.erp.product.ProductTypes.ProductStatus;
import static com.jiugeng.erp.product.ProductTypes.ProductView;
import static com.jiugeng.erp.product.ProductTypes.VariantInput;
import static com.jiugeng.erp.product.ProductTypes.VariantStatus;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/products")
class ProductController {
    private final ProductService service;

    ProductController(ProductService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('products.read')")
    List<ProductView> products() { return service.products(); }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority('products.read')")
    ProductView product(@PathVariable String productId) { return service.product(productId); }

    @PostMapping
    @PreAuthorize("hasAuthority('products.write')")
    ProductView create(@Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.create(request.input(), actor);
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAuthority('products.write')")
    ProductView update(@PathVariable String productId, @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.update(productId, request.input(), actor);
    }

    @PostMapping("/{productId}/disable")
    @PreAuthorize("hasAuthority('products.write')")
    ProductView disable(@PathVariable String productId, @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.disable(productId, actor);
    }

    record ProductRequest(
            @NotBlank @Size(max = 64) String productCode,
            @NotBlank @Size(max = 200) String nameZh,
            @Size(max = 200) String nameEn,
            @Size(max = 100) String brand,
            @Size(max = 100) String internalCategory,
            @Size(max = 100) String productType,
            @Size(max = 1000) String description,
            ProductStatus status,
            @NotEmpty @Size(max = 100) List<@Valid VariantRequest> variants) {
        ProductInput input() {
            return new ProductInput(productCode, nameZh, nameEn, brand, internalCategory, productType, description,
                    status, variants.stream().map(VariantRequest::input).toList());
        }
    }

    record VariantRequest(
            String id,
            @NotBlank @Size(max = 64) String skuCode,
            @NotBlank @Size(max = 200) String variantName,
            @Size(max = 64) String barcode,
            @Size(max = 200) String material,
            @Size(max = 300) String specification,
            @Size(max = 200) String itemDimensions,
            @Size(max = 200) String packageDimensions,
            @PositiveOrZero BigDecimal packageWeight,
            @Size(max = 8) String packageWeightUnit,
            VariantStatus status,
            @NotEmpty @Size(max = 30) List<@Valid AttributeRequest> attributes) {
        VariantInput input() {
            return new VariantInput(id, skuCode, variantName, barcode, material, specification, itemDimensions,
                    packageDimensions, packageWeight, packageWeightUnit, status,
                    attributes.stream().map(AttributeRequest::input).toList());
        }
    }

    record AttributeRequest(
            @NotBlank @Size(max = 64) String attributeCode,
            @NotBlank @Size(max = 300) String attributeValue,
            @NotNull @PositiveOrZero Integer displayOrder) {
        AttributeInput input() { return new AttributeInput(attributeCode, attributeValue, displayOrder); }
    }
}
