package com.jiugeng.erp.product;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductLookupService implements ProductLookup {
    private final ProductRepository repository;

    ProductLookupService(ProductRepository repository) { this.repository = repository; }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductReference> findById(String productId) {
        return repository.findById(productId).map(product -> new ProductReference(product.id(), product.productCode(),
                product.nameZh(), product.status() == ProductTypes.ProductStatus.ACTIVE));
    }
}
