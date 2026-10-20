package com.jiugeng.erp.product;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductLookupService implements ProductLookup {
    private final ProductRepository repository;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    ProductLookupService(ProductRepository repository,org.springframework.jdbc.core.simple.JdbcClient jdbc) { this.repository = repository;this.jdbc=jdbc; }

    @Override
    public Optional<SkuReference> findExistingSku(String productId,String variantId) {
        return jdbc.sql("SELECT v.id,v.sku_code,v.variant_name,p.status,v.lifecycle_status FROM product_variant v JOIN product p ON p.id=v.product_id WHERE p.id=:product AND v.id=:variant")
            .param("product",productId).param("variant",variantId).query((r,n)->new SkuReference(r.getString(1),r.getString(2),r.getString(3),"ACTIVE".equals(r.getString(4)) && "ACTIVE".equals(r.getString(5)))).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SkuReference> findSku(String productId, String variantId) {
        return repository.findById(productId, false).flatMap(product -> product.variants().stream()
            .filter(variant -> variant.id().equals(variantId))
            .map(variant -> new SkuReference(variant.id(), variant.skuCode(), variant.variantName(),
                product.status() == ProductTypes.ProductStatus.ACTIVE && variant.status() == ProductTypes.VariantStatus.ACTIVE))
            .findFirst());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<String> skuCodes(String productId) {
        return repository.findById(productId,false).map(product -> product.variants().stream()
                .map(ProductTypes.VariantView::skuCode).toList()).orElse(java.util.List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductReference> findById(String productId) {
        return repository.findById(productId, false).map(product -> new ProductReference(product.id(), product.productCode(),
                product.productName(), product.status() == ProductTypes.ProductStatus.ACTIVE));
    }
}
