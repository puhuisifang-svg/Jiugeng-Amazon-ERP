package com.jiugeng.erp.product;

import java.util.Optional;

/** Public, read-only product boundary for other application modules. */
public interface ProductLookup {
    Optional<ProductReference> findById(String productId);
    default Optional<SkuReference> findSku(String productId, String variantId) { return Optional.empty(); }
    /** For an existing document only; deleted identities remain available, disabled status is still enforced. */
    default Optional<SkuReference> findExistingSku(String productId,String variantId) { return findSku(productId,variantId); }
    default java.util.List<String> skuCodes(String productId) { return java.util.List.of(); }

    record ProductReference(String id, String productCode, String name, boolean active) {}
    record SkuReference(String id, String skuCode, String name, boolean active) {}
}
