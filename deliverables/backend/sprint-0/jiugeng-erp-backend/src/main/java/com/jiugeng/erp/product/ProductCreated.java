package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.List;

/** Synchronous creation event: dependent catalog records must join the product transaction. */
public record ProductCreated(String productId, List<Sku> skus, AuthenticatedActor actor) {
    public ProductCreated { skus = List.copyOf(skus); }
    public record Sku(String id, String code, String name) {}
    public static final class RegistrationException extends RuntimeException {
        public RegistrationException(String message) { super(message); }
    }
}
