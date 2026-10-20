package com.jiugeng.erp.customer;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.product.ProductCreated;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class DefaultCustomerProductRegistration {
    private final JdbcClient jdbc;
    private final AuditTrail audit;

    DefaultCustomerProductRegistration(JdbcClient jdbc, AuditTrail audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // Intentionally synchronous, not AFTER_COMMIT: either both catalogs save, or neither does.
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void register(ProductCreated event) {
        String customer = jdbc.sql("SELECT COALESCE(default_customer_id,'') FROM customer_preferences WHERE id=1 FOR UPDATE")
            .query(String.class).optional().orElse("");
        String status = jdbc.sql("SELECT status FROM customer WHERE id=:id FOR UPDATE")
            .param("id", customer).query(String.class).optional().orElse("");
        if (!"ACTIVE".equals(status)) {
            throw new ProductCreated.RegistrationException("请先在客户信息中设置启用的默认客户，再新增产品");
        }
        for (var sku : event.skus()) {
            String id = UUID.randomUUID().toString();
            // The association is active; shipping still requires an active parent and SKU.
            jdbc.sql("""
                INSERT INTO customer_product(id,customer_id,product_id,variant_id,sku_code,product_name,
                    supply_price,price_unit,status,created_by,updated_by)
                VALUES(:id,:customer,:product,:variant,:sku,:name,NULL,'套','ACTIVE',:actor,:actor)
                """).params(Map.of("id", id, "customer", customer, "product", event.productId(),
                    "variant", sku.id(), "sku", sku.code(), "name", sku.name(), "actor", event.actor().userId())).update();
            var actor = event.actor();
            audit.record(new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(),
                "CUSTOMER_PRODUCT_CREATE", "CUSTOMER_PRODUCT", id, true, null,
                Map.of("customerId", customer, "variantId", sku.id(), "supplyPrice", "未定价",
                    "priceUnit", "套", "status", "ACTIVE", "source", "PRODUCT_CREATE"),
                "新增产品自动建立默认客户商品"));
        }
    }
}
