package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.ProductMaterialRelationTypes.ApplyAllInput;
import static com.jiugeng.erp.materialinventory.ProductMaterialRelationTypes.RelationInput;
import static com.jiugeng.erp.materialinventory.ProductMaterialRelationTypes.RelationView;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductMaterialRelationService {
    private final ProductMaterialRelationRepository repository;
    private final AuditTrail auditTrail;

    ProductMaterialRelationService(ProductMaterialRelationRepository repository, AuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    List<RelationView> relations(String productId, String variantId) {
        boolean hasProduct = productId != null && !productId.isBlank();
        boolean hasVariant = variantId != null && !variantId.isBlank();
        if (hasProduct == hasVariant) {
            throw new MaterialException(400, "RELATION_QUERY_INVALID", "productId 与 variantId 必须且只能提供一个");
        }
        return hasProduct ? repository.findByProduct(productId.trim()) : repository.findByVariant(variantId.trim());
    }

    @Transactional
    RelationView create(RelationInput input, AuthenticatedActor actor) {
        Validated valid = validate(input, null);
        String id = UUID.randomUUID().toString();
        try {
            repository.insert(id, valid.productId(), valid.variantId(), valid.materialId(), valid.quantity(),
                    valid.unit(), valid.lossRate(), valid.remark(), actor.userId());
            RelationView created = relation(id);
            auditTrail.record(operation(actor, "PRODUCT_MATERIAL_CREATE", id, null, snapshot(created), "新增 SKU 辅料关系"));
            return created;
        } catch (DataIntegrityViolationException exception) {
            throw new MaterialException(409, "PRODUCT_MATERIAL_EXISTS", "该 SKU 已关联此辅料");
        }
    }

    @Transactional
    RelationView update(String id, RelationInput input, AuthenticatedActor actor) {
        RelationView before = relation(id);
        if (!before.productId().equals(trim(input.productId())) || !before.variantId().equals(trim(input.variantId()))
                || !before.materialId().equals(trim(input.materialId()))) {
            throw new MaterialException(409, "PRODUCT_MATERIAL_REFERENCE_IMMUTABLE", "产品、SKU 与辅料引用不可修改");
        }
        Validated valid = validate(input, id);
        repository.update(id, valid.quantity(), valid.unit(), valid.lossRate(), valid.remark(),
                input.enabled() == null || input.enabled(), actor.userId());
        RelationView updated = relation(id);
        auditTrail.record(operation(actor, "PRODUCT_MATERIAL_UPDATE", id, snapshot(before), snapshot(updated), "调整 SKU 辅料关系"));
        return updated;
    }

    @Transactional
    RelationView disable(String id, AuthenticatedActor actor) {
        RelationView before = relation(id);
        if (!before.enabled()) return before;
        repository.disable(id, actor.userId());
        RelationView disabled = relation(id);
        auditTrail.record(operation(actor, "PRODUCT_MATERIAL_DISABLE", id, snapshot(before), snapshot(disabled), "逻辑停用 SKU 辅料关系"));
        return disabled;
    }

    @Transactional
    List<RelationView> applyToAll(ApplyAllInput input, AuthenticatedActor actor) {
        String productId = required(input.productId(), "产品", 36);
        String materialId = required(input.materialId(), "辅料", 36);
        ensureActiveProduct(productId);
        ensureActiveMaterial(materialId);
        BigDecimal quantity = quantity(input.standardQuantity());
        BigDecimal lossRate = lossRate(input.lossRate());
        String unit = required(input.unit(), "单位", 32);
        String remark = optional(input.remark(), 1000);
        List<String> variants = repository.activeVariantIds(productId);
        if (variants.isEmpty()) throw new MaterialException(409, "ACTIVE_VARIANT_REQUIRED", "产品没有可用的启用 SKU");
        int created = 0;
        for (String variantId : variants) {
            if (!repository.exists(variantId, materialId, null)) {
                repository.insert(UUID.randomUUID().toString(), productId, variantId, materialId, quantity, unit,
                        lossRate, remark, actor.userId());
                created++;
            }
        }
        List<RelationView> after = repository.findByProduct(productId);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("productId", productId); summary.put("materialId", materialId);
        summary.put("createdCount", created); summary.put("activeVariantCount", variants.size());
        auditTrail.record(operation(actor, "PRODUCT_MATERIAL_APPLY_ALL", productId, null, summary,
                "将辅料应用到全部启用 SKU；已有独立关系不覆盖"));
        return after;
    }

    private RelationView relation(String id) {
        return repository.findById(id).orElseThrow(() -> new MaterialException(404,
                "PRODUCT_MATERIAL_NOT_FOUND", "SKU 辅料关系不存在：" + id));
    }

    private Validated validate(RelationInput input, String excludingId) {
        String productId = required(input.productId(), "产品", 36);
        String variantId = required(input.variantId(), "SKU", 36);
        String materialId = required(input.materialId(), "辅料", 36);
        ensureActiveProduct(productId);
        if (!repository.activeVariantInProduct(variantId, productId)) {
            throw new MaterialException(409, "ACTIVE_VARIANT_REQUIRED", "只能引用当前产品的启用 SKU");
        }
        ensureActiveMaterial(materialId);
        if (repository.exists(variantId, materialId, excludingId)) {
            throw new MaterialException(409, "PRODUCT_MATERIAL_EXISTS", "该 SKU 已关联此辅料");
        }
        return new Validated(productId, variantId, materialId, quantity(input.standardQuantity()),
                required(input.unit(), "单位", 32), lossRate(input.lossRate()), optional(input.remark(), 1000));
    }

    private void ensureActiveProduct(String id) {
        if (!repository.activeProduct(id)) throw new MaterialException(409, "ACTIVE_PRODUCT_REQUIRED", "只能引用启用产品");
    }

    private void ensureActiveMaterial(String id) {
        if (!repository.activeMaterial(id)) throw new MaterialException(409, "ACTIVE_MATERIAL_REQUIRED", "只能引用启用辅料");
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new MaterialException(400, "STANDARD_QUANTITY_INVALID", "标准用量必须大于 0");
        }
        return value;
    }

    private BigDecimal lossRate(BigDecimal value) {
        BigDecimal result = value == null ? BigDecimal.ZERO : value;
        if (result.signum() < 0 || result.compareTo(BigDecimal.ONE) > 0) {
            throw new MaterialException(400, "LOSS_RATE_INVALID", "损耗率必须在 0 到 1 之间");
        }
        return result;
    }

    private String required(String value, String label, int max) {
        String result = trim(value);
        if (result == null || result.length() > max) throw new MaterialException(400, "RELATION_FIELD_INVALID", label + "不能为空");
        return result;
    }

    private String optional(String value, int max) {
        String result = trim(value);
        if (result != null && result.length() > max) throw new MaterialException(400, "RELATION_FIELD_INVALID", "字段长度超限");
        return result;
    }

    private String trim(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor, String action, String id,
            Map<String, ?> before, Map<String, ?> after, String detail) {
        return new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(), action,
                "PRODUCT_AUXILIARY_MATERIAL", id, true, before, after, detail);
    }

    private Map<String, Object> snapshot(RelationView relation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", relation.id()); value.put("productId", relation.productId());
        value.put("variantId", relation.variantId()); value.put("materialId", relation.materialId());
        value.put("standardQuantity", relation.standardQuantity()); value.put("unit", relation.unit());
        value.put("lossRate", relation.lossRate()); value.put("enabled", relation.enabled());
        return value;
    }

    private record Validated(String productId, String variantId, String materialId, BigDecimal quantity,
            String unit, BigDecimal lossRate, String remark) {}
}
