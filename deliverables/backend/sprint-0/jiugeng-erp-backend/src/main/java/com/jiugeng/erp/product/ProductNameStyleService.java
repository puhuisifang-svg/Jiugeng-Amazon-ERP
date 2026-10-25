package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Saved within the aggregate's permission-checked, optimistic-locking transaction. */
@Service
class ProductNameStyleService {
    private final JdbcClient jdbc;
    private final AuditTrail audit;

    ProductNameStyleService(JdbcClient jdbc, AuditTrail audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void save(String productId, String variantId, boolean highlighted, AuthenticatedActor actor) {
        boolean before = jdbc.sql("SELECT name_highlighted FROM product_variant WHERE id=:id AND product_id=:product")
                .param("id", variantId).param("product", productId).query(Boolean.class).single();
        if (before == highlighted) return;
        jdbc.sql("UPDATE product_variant SET name_highlighted=:highlighted WHERE id=:id AND product_id=:product")
                .param("highlighted", highlighted).param("id", variantId).param("product", productId).update();
        audit.record(new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(),
                "PRODUCT_NAME_STYLE_UPDATE", "PRODUCT", productId, true,
                Map.of("variantId", variantId, "nameHighlighted", before),
                Map.of("variantId", variantId, "nameHighlighted", highlighted), "修改 SKU 名称标红设置"));
    }
}
