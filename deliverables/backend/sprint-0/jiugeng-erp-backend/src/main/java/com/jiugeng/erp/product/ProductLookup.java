package com.jiugeng.erp.product;

import java.util.Optional;

/** Public, read-only product boundary for other application modules. */
public interface ProductLookup {
    Optional<ProductReference> findById(String productId);
    default java.util.List<String> skuCodes(String productId) { return java.util.List.of(); }

    record ProductReference(String id, String productCode, String name, boolean active) {}
}
