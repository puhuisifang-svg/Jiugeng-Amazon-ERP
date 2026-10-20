package com.jiugeng.erp.customer;

import java.util.Optional;

/** Read-only shipping boundary; deliberately excludes invoice and price data. */
public interface CustomerProductLookup {
    Optional<ShippingProduct> active(String id);
    /** Only for a persisted document retaining its original customer-product identity. */
    default Optional<ShippingProduct> forExisting(String id) { return active(id); }
    record ShippingProduct(String id, String customerId, String customerName, String productId,
        String variantId, String customerProductCode, String requirements) {}
}
