package com.jiugeng.erp.product;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductLookupService implements ProductLookup {
    private final ProductRepository repository;
    private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

    ProductLookupService(ProductRepository repository,org.springframework.jdbc.core.simple.JdbcClient jdbc) { this.repository = repository;this.jdbc=jdbc; }

    @Override @Transactional(readOnly=true)
    public java.util.List<SamplePresentation> samplePresentations(java.util.List<String> ids) {
        if(ids.isEmpty()) return java.util.List.of();
        if(ids.size()>10000) throw new IllegalArgumentException("Too many product identities");
        return jdbc.sql("SELECT p.id product_id,v.id,p.product_name,v.variant_name,p.status,v.lifecycle_status,p.deleted_at parent_deleted,v.deleted_at,v.main_image_asset_id,v.main_image_url FROM product_variant v JOIN product p ON p.id=v.product_id WHERE v.id IN (:ids)")
            .param("ids",ids).query((r,n)->new SamplePresentation(r.getString("product_id"),r.getString("id"),r.getString("product_name"),r.getString("variant_name"),
                r.getTimestamp("parent_deleted")!=null||r.getTimestamp("deleted_at")!=null?"DELETED":
                "ACTIVE".equals(r.getString("status"))&&"ACTIVE".equals(r.getString("lifecycle_status"))?"ACTIVE":"DISABLED",r.getString("main_image_asset_id"),r.getString("main_image_url"))).list();
    }

    @Override
    public String skuSpecification(String productId,String variantId) {
        return repository.findById(productId,false).flatMap(p->p.variants().stream().filter(v->v.id().equals(variantId)).findFirst()).map(v->{
            if(v.specification()!=null&&!v.specification().isBlank())return v.specification();
            if(v.itemLengthCm()!=null&&v.itemWidthCm()!=null&&v.itemHeightCm()!=null)return v.itemLengthCm().stripTrailingZeros().toPlainString()+" × "+v.itemWidthCm().stripTrailingZeros().toPlainString()+" × "+v.itemHeightCm().stripTrailingZeros().toPlainString()+" cm";
            return v.itemDimensions()==null?"":v.itemDimensions();
        }).orElse("");
    }
    @Override
    public NamePresentation namePresentation(String productId, String variantId, String snapshotName) {
        if (variantId == null) return null;
        return jdbc.sql("SELECT variant_name,name_highlighted,name_styles FROM product_variant WHERE product_id=:product AND id=:variant")
            .param("product", productId).param("variant", variantId).query((r,n) -> {
                if (!java.util.Objects.equals(snapshotName, r.getString(1))) return null;
                var spans = ProductNameStyleService.decode(r.getString(3), snapshotName);
                return new NamePresentation(r.getBoolean(2), spans == null ? null : spans.stream()
                    .map(s -> new NameSpan(s.start(), s.end(), s.color().name(), s.bold())).toList());
            }).optional().orElse(null);
    }

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
