package com.jiugeng.erp.product;

import java.util.Optional;

/** Public, read-only product boundary for other application modules. */
public interface ProductLookup {
    Optional<ProductReference> findById(String productId);

    record ProductReference(String id, String productCode, String name, boolean active) {}
}
