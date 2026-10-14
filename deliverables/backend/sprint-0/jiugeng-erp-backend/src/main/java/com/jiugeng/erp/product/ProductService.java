package com.jiugeng.erp.product;

import static com.jiugeng.erp.product.ProductTypes.AttributeInput;
import static com.jiugeng.erp.product.ProductTypes.AttributeView;
import static com.jiugeng.erp.product.ProductTypes.ProductInput;
import static com.jiugeng.erp.product.ProductTypes.ProductStatus;
import static com.jiugeng.erp.product.ProductTypes.ProductView;
import static com.jiugeng.erp.product.ProductTypes.VariantInput;
import static com.jiugeng.erp.product.ProductTypes.VariantStatus;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductService {
    private final ProductRepository repository;
    private final AuditTrail auditTrail;

    ProductService(ProductRepository repository, AuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    List<ProductView> products() { return repository.findAll(); }

    @Transactional(readOnly = true)
    ProductView product(String id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    ProductView create(ProductInput input, AuthenticatedActor actor) {
        ValidatedProduct valid = validate(input, null);
        String id = UUID.randomUUID().toString();
        try {
            repository.insertProduct(id, valid.code(), valid.normalizedCode(), valid.nameZh(), valid.nameEn(),
                    valid.brand(), valid.category(), valid.type(), valid.description(), valid.status(), actor.userId());
            saveVariants(id, valid.variants(), false);
            ProductView created = product(id);
            auditTrail.record(operation(actor, "PRODUCT_CREATE", id, null, snapshot(created), "创建产品及 SKU"));
            return created;
        } catch (DataIntegrityViolationException exception) {
            throw conflictFromDatabase(exception);
        }
    }

    @Transactional
    ProductView update(String id, ProductInput input, AuthenticatedActor actor) {
        ProductView before = product(id);
        if (before.status() == ProductStatus.DISABLED) {
            throw new ProductException(409, "PRODUCT_DISABLED", "停用产品不可编辑");
        }
        ValidatedProduct valid = validate(input, id);
        try {
            repository.updateProduct(id, valid.code(), valid.normalizedCode(), valid.nameZh(), valid.nameEn(),
                    valid.brand(), valid.category(), valid.type(), valid.description(), valid.status(), actor.userId());
            saveVariants(id, valid.variants(), true);
            ProductView updated = product(id);
            auditTrail.record(operation(actor, "PRODUCT_UPDATE", id, snapshot(before), snapshot(updated), "更新产品及 SKU"));
            return updated;
        } catch (DataIntegrityViolationException exception) {
            throw conflictFromDatabase(exception);
        }
    }

    @Transactional
    ProductView disable(String id, AuthenticatedActor actor) {
        ProductView before = product(id);
        if (before.status() == ProductStatus.DISABLED) return before;
        repository.disableProduct(id, actor.userId());
        ProductView disabled = product(id);
        auditTrail.record(operation(actor, "PRODUCT_DISABLE", id, snapshot(before), snapshot(disabled), "逻辑停用产品及 SKU"));
        return disabled;
    }

    private ValidatedProduct validate(ProductInput input, String productId) {
        String code = required(input.productCode(), "产品编码", 64);
        String normalizedCode = ProductRepository.normalize(code);
        if (repository.productCodeExists(normalizedCode, productId)) {
            throw new ProductException(409, "PRODUCT_CODE_EXISTS", "产品编码已存在（忽略大小写）");
        }
        String nameZh = required(input.nameZh(), "中文名称", 200);
        ProductStatus status = input.status() == null ? ProductStatus.DRAFT : input.status();
        if (status == ProductStatus.DISABLED) {
            throw new ProductException(400, "PRODUCT_STATUS_INVALID", "请使用停用操作停用产品");
        }
        List<VariantInput> source = input.variants() == null ? List.of() : input.variants();
        if (source.isEmpty() || source.size() > 100) {
            throw new ProductException(400, "PRODUCT_VARIANTS_INVALID", "产品必须包含 1 至 100 个 SKU");
        }
        Set<String> skuCodes = new HashSet<>();
        Set<String> signatures = new HashSet<>();
        List<ValidatedVariant> variants = source.stream().map(variant -> validateVariant(variant, productId)).toList();
        for (ValidatedVariant variant : variants) {
            if (!skuCodes.add(variant.normalizedSku())) {
                throw new ProductException(409, "SKU_CODE_EXISTS", "SKU 编码重复（忽略大小写）");
            }
            if (!signatures.add(variant.signature())) {
                throw new ProductException(409, "VARIANT_COMBINATION_EXISTS", "同一产品内变体属性组合重复");
            }
        }
        return new ValidatedProduct(code, normalizedCode, nameZh, optional(input.nameEn(), 200),
                optional(input.brand(), 100), optional(input.internalCategory(), 100),
                optional(input.productType(), 100), optional(input.description(), 1000), status, variants);
    }

    private ValidatedVariant validateVariant(VariantInput input, String productId) {
        String id = blankToNull(input.id());
        if (id != null && (productId == null || !repository.variantBelongsToProduct(id, productId))) {
            throw new ProductException(400, "VARIANT_REFERENCE_INVALID", "SKU 不属于当前产品");
        }
        String sku = required(input.skuCode(), "SKU 编码", 64);
        String normalizedSku = ProductRepository.normalize(sku);
        if (repository.skuCodeExists(normalizedSku, id)) {
            throw new ProductException(409, "SKU_CODE_EXISTS", "SKU 编码已存在（忽略大小写）");
        }
        String barcode = optional(input.barcode(), 64);
        String normalizedBarcode = barcode == null ? null : ProductRepository.normalize(barcode);
        if (repository.barcodeExists(normalizedBarcode, id)) {
            throw new ProductException(409, "BARCODE_EXISTS", "商品条码已存在（忽略大小写）");
        }
        String weightUnit = optional(input.packageWeightUnit(), 8);
        if (input.packageWeight() != null && input.packageWeight().signum() <= 0) {
            throw new ProductException(400, "PACKAGE_WEIGHT_INVALID", "包装重量必须大于 0");
        }
        if ((input.packageWeight() == null) != (weightUnit == null)
                || weightUnit != null && !Set.of("G", "KG", "OZ", "LB").contains(weightUnit.toUpperCase(Locale.ROOT))) {
            throw new ProductException(400, "PACKAGE_WEIGHT_UNIT_INVALID", "包装重量与 G/KG/OZ/LB 单位必须同时填写");
        }
        List<AttributeInput> source = input.attributes() == null ? List.of() : input.attributes();
        if (source.isEmpty() || source.size() > 30) {
            throw new ProductException(400, "VARIANT_ATTRIBUTES_INVALID", "每个 SKU 必须包含 1 至 30 个变体属性");
        }
        Set<String> codes = new HashSet<>();
        List<AttributeView> attributes = source.stream().map(attribute -> {
            String code = required(attribute.attributeCode(), "属性编码", 64);
            String normalized = ProductRepository.normalize(code);
            if (!codes.add(normalized)) {
                throw new ProductException(409, "ATTRIBUTE_CODE_EXISTS", "同一 SKU 下属性编码重复（忽略大小写）");
            }
            if (attribute.displayOrder() < 0) {
                throw new ProductException(400, "ATTRIBUTE_ORDER_INVALID", "属性展示顺序不能小于 0");
            }
            return new AttributeView(code, required(attribute.attributeValue(), "属性值", 300), attribute.displayOrder());
        }).toList();
        String signature = signature(attributes);
        return new ValidatedVariant(id, sku, normalizedSku, required(input.variantName(), "规格名称", 200), barcode,
                normalizedBarcode, optional(input.material(), 200), optional(input.specification(), 300),
                optional(input.itemDimensions(), 200), optional(input.packageDimensions(), 200), input.packageWeight(),
                weightUnit == null ? null : weightUnit.toUpperCase(Locale.ROOT),
                input.status() == null ? VariantStatus.ACTIVE : input.status(), attributes, signature);
    }

    private void saveVariants(String productId, List<ValidatedVariant> variants, boolean updating) {
        List<String> existingIds = updating ? repository.findVariantIds(productId) : List.of();
        if (updating) {
            repository.reserveVariantSignatures(productId, variants.stream().map(ValidatedVariant::id)
                    .filter(java.util.Objects::nonNull).toList());
        }
        for (ValidatedVariant variant : variants) {
            String id = variant.id() == null ? UUID.randomUUID().toString() : variant.id();
            if (variant.id() == null) {
                repository.insertVariant(id, productId, variant.sku(), variant.normalizedSku(), variant.name(),
                        variant.barcode(), variant.normalizedBarcode(), variant.material(), variant.specification(),
                        variant.itemDimensions(), variant.packageDimensions(), variant.packageWeight(),
                        variant.packageWeightUnit(), variant.status(),
                        variant.signature());
            } else {
                repository.updateVariant(id, variant.sku(), variant.normalizedSku(), variant.name(), variant.barcode(),
                        variant.normalizedBarcode(), variant.material(), variant.specification(), variant.itemDimensions(),
                        variant.packageDimensions(), variant.packageWeight(), variant.packageWeightUnit(), variant.status(), variant.signature());
            }
            repository.replaceAttributes(id, variant.attributes());
        }
        if (updating) {
            Set<String> retained = variants.stream().map(ValidatedVariant::id)
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            existingIds.stream().filter(id -> !retained.contains(id)).forEach(repository::disableVariant);
        }
    }

    private String signature(List<AttributeView> attributes) {
        String canonical = attributes.stream()
                .sorted(java.util.Comparator.comparing(value -> ProductRepository.normalize(value.attributeCode())))
                .map(value -> ProductRepository.normalize(value.attributeCode()) + "\u001f"
                        + value.attributeValue().trim().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.joining("|"));
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private String required(String value, String label, int max) {
        String result = blankToNull(value);
        if (result == null || result.length() > max) {
            throw new ProductException(400, "PRODUCT_FIELD_INVALID", label + "不能为空且长度不能超过 " + max);
        }
        return result;
    }

    private String optional(String value, int max) {
        String result = blankToNull(value);
        if (result != null && result.length() > max) {
            throw new ProductException(400, "PRODUCT_FIELD_INVALID", "字段长度不能超过 " + max);
        }
        return result;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ProductException notFound(String id) {
        return new ProductException(404, "PRODUCT_NOT_FOUND", "产品不存在：" + id);
    }

    private ProductException conflictFromDatabase(DataIntegrityViolationException exception) {
        return new ProductException(409, "PRODUCT_CONFLICT", "产品编码、SKU、条码或变体属性组合与现有数据冲突");
    }

    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor, String action, String id,
            Map<String, ?> before, Map<String, ?> after, String detail) {
        return new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(), action,
                "PRODUCT", id, true, before, after, detail);
    }

    private Map<String, Object> snapshot(ProductView product) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", product.id());
        value.put("productCode", product.productCode());
        value.put("nameZh", product.nameZh());
        value.put("status", product.status());
        value.put("skuCodes", product.variants().stream().map(ProductTypes.VariantView::skuCode).toList());
        return value;
    }

    private record ValidatedProduct(String code, String normalizedCode, String nameZh, String nameEn, String brand,
            String category, String type, String description, ProductStatus status, List<ValidatedVariant> variants) {}

    private record ValidatedVariant(String id, String sku, String normalizedSku, String name, String barcode,
            String normalizedBarcode, String material, String specification, String itemDimensions,
            String packageDimensions, java.math.BigDecimal packageWeight, String packageWeightUnit,
            VariantStatus status,
            List<AttributeView> attributes, String signature) {}
}
