package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** ERP-021-003 version 2.0/2.1 import adapter. Legacy attributes and retired fields remain read-only. */
@Service
class ProductImportService implements ProductImportGateway {
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED");
    private static final BigDecimal MAX_GRAMS = new BigDecimal("999999999.999");
    private static final BigDecimal MAX_DIMENSION = new BigDecimal("999999.999");
    private final JdbcClient jdbc;
    private final AuditTrail audit;

    ProductImportService(JdbcClient jdbc, AuditTrail audit) { this.jdbc = jdbc; this.audit = audit; }

    @Override public Validation validateProducts(List<ImportRow> rows) {
        List<Error> errors = new ArrayList<>(); Set<String> codes = new HashSet<>();
        for (ImportRow row : rows) {
            if (!"导入模板".equals(row.sheet())) continue;
            String code = value(row, "product_code");
            requiredCode(errors, row, "product_code", code);
            required(errors, row, "product_name", value(row, "product_name"), 128);
            optional(errors, row, "product_type", value(row, "product_type"), 100);
            optional(errors, row, "description", value(row, "description"), 1000);
            enumeration(errors, row, "status", value(row, "status"), "DRAFT");
            if (!code.isBlank() && !codes.add(normalize(code))) errors.add(error(row, "product_code",
                    "PRODUCT_CODE_DUPLICATED_IN_FILE", "文件内产品编码重复（忽略大小写）", code));
        }
        return new Validation(List.copyOf(errors));
    }

    @Override public Validation validateVariants(List<ImportRow> rows) {
        List<Error> errors = new ArrayList<>(); Set<String> skus = new HashSet<>();
        List<ImportRow> variants = rows.stream().filter(row -> "variants_导入模板".equals(row.sheet())).toList();
        for (ImportRow row : variants) {
            String sku = value(row, "sku_code"); String product = value(row, "product_code");
            requiredCode(errors, row, "sku_code", sku); requiredCode(errors, row, "product_code", product);
            required(errors, row, "variant_name", value(row, "variant_name"), 128);
            optional(errors, row, "item_dimensions", value(row, "item_dimensions"), 200);
            optional(errors, row, "package_dimensions", value(row, "package_dimensions"), 200);
            validateDimensions(errors, row, "item", "item_dimensions");
            validateDimensions(errors, row, "package", "package_dimensions");
            validateWeight(errors, row); enumeration(errors, row, "status", value(row, "status"), "DRAFT");
            if (!sku.isBlank() && !skus.add(normalize(sku))) errors.add(error(row, "sku_code",
                    "SKU_CODE_DUPLICATED_IN_FILE", "文件内 SKU 编码重复（忽略大小写）", sku));
        }
        validateDatabaseReferences(variants, errors);
        return new Validation(List.copyOf(errors));
    }

    @Override public Result commitProducts(List<ImportRow> rows, AuthenticatedActor actor) {
        Validation validation = validateProducts(rows); if (!validation.valid()) throw new IllegalArgumentException("Products must be valid before commit");
        int created = 0, updated = 0, unchanged = 0;
        for (ImportRow row : rows) {
            if (!"导入模板".equals(row.sheet())) continue;
            ProductData next = product(row); ProductData current = findProduct(next.normalizedCode());
            if (current == null) {
                String id = UUID.randomUUID().toString();
                jdbc.sql("""
                        INSERT INTO product (id,product_code,product_code_normalized,name_zh,product_name,
                            product_type,description,status,created_by,updated_by)
                        VALUES (:id,:code,:normalized,:name,:name,:type,:description,:status,:actor,:actor)
                        """).param("id", id).param("code", next.code()).param("normalized", next.normalizedCode())
                        .param("name", next.name()).param("type", next.type()).param("description", next.description())
                        .param("status", next.status()).param("actor", actor.userId()).update();
                audit.record(operation(actor, "PRODUCT_IMPORT_CREATE", "PRODUCT", id, null, productSnapshot(next))); created++;
            } else if (current.sameBusinessFields(next)) unchanged++;
            else {
                jdbc.sql("""
                        UPDATE product SET product_code=:code,product_code_normalized=:normalized,product_name=:name,name_zh=:name,
                            product_type=:type,description=:description,status=:status,version=version+1,
                            updated_by=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id
                        """).param("id", current.id()).param("code", next.code()).param("normalized", next.normalizedCode()).param("name", next.name())
                        .param("type", next.type()).param("description", next.description()).param("status", next.status())
                        .param("actor", actor.userId()).update();
                audit.record(operation(actor, "PRODUCT_IMPORT_UPDATE", "PRODUCT", current.id(), productSnapshot(current), productSnapshot(next))); updated++;
            }
        }
        return new Result(created, updated, unchanged);
    }

    @Override public Result commitVariants(List<ImportRow> rows, AuthenticatedActor actor) {
        Validation validation = validateVariants(rows); if (!validation.valid()) throw new IllegalArgumentException("Variants must be valid before commit");
        List<VariantData> nextRows = rows.stream().filter(row -> "variants_导入模板".equals(row.sheet())).map(this::variant).toList();
        int created = 0, updated = 0, unchanged = 0;
        for (VariantData next : nextRows) {
            VariantData current = findVariant(next.normalizedSku()); String productId = productId(next.normalizedProductCode());
            if (current == null) {
                String id = UUID.randomUUID().toString(); insertVariant(id, productId, next);
                audit.record(operation(actor, "VARIANT_IMPORT_CREATE", "PRODUCT_VARIANT", id, null, variantSnapshot(next))); created++;
            } else if (current.sameBusinessFields(next)) unchanged++;
            else {
                updateVariant(current.id(), productId, next);
                audit.record(operation(actor, "VARIANT_IMPORT_UPDATE", "PRODUCT_VARIANT", current.id(), variantSnapshot(current), variantSnapshot(next))); updated++;
            }
        }
        return new Result(created, updated, unchanged);
    }

    private void validateDatabaseReferences(List<ImportRow> rows, List<Error> errors) {
        for (ImportRow row : rows) {
            String productCode = normalize(value(row, "product_code"));
            var found = jdbc.sql("SELECT id,status FROM product WHERE product_code_normalized=:code").param("code", productCode)
                    .query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2))).optional();
            if (found.isEmpty() || "DISABLED".equals(found.get().getValue())) errors.add(error(row, "product_code",
                    "PRODUCT_REFERENCE_INVALID", "产品不存在或已停用", value(row, "product_code")));
            VariantData existing = findVariant(normalize(value(row, "sku_code")));
            if (existing != null && !existing.normalizedProductCode().equals(productCode)) errors.add(error(row,
                    "product_code", "SKU_PRODUCT_CONFLICT", "现有 SKU 属于其他产品", value(row, "product_code")));
        }
    }

    private void validateWeight(List<Error> errors, ImportRow row) {
        String raw = value(row, "package_weight_grams"); if (raw.isBlank()) return;
        try { BigDecimal grams = new BigDecimal(raw); if (grams.signum() <= 0 || grams.scale() > 3 || grams.compareTo(MAX_GRAMS) > 0) throw new NumberFormatException(); }
        catch (NumberFormatException exception) { errors.add(error(row, "package_weight_grams", "PACKAGE_WEIGHT_INVALID", "包装重量必须为不超过三位小数且不超过 999999999.999 的正克数", raw)); }
    }

    private void validateDimensions(List<Error> errors, ImportRow row, String prefix, String legacyField) {
        if (!row.data().containsKey(prefix + "_length_cm")) return;
        String[] fields = {prefix + "_length_cm", prefix + "_width_cm", prefix + "_height_cm"};
        int populated = 0;
        for (String field : fields) {
            String raw = value(row, field); if (raw.isBlank()) continue; populated++;
            try { BigDecimal value = new BigDecimal(raw); if (value.signum() <= 0 || value.scale() > 3 || value.compareTo(MAX_DIMENSION) > 0) throw new NumberFormatException(); }
            catch (NumberFormatException exception) { errors.add(error(row, field, "DIMENSION_INVALID", "尺寸必须为不超过三位小数且不超过 999999.999 的正厘米数", raw)); }
        }
        if (populated != 0 && populated != 3) errors.add(error(row, fields[0], "DIMENSION_GROUP_INCOMPLETE", "长、宽、高必须同时填写", value(row, fields[0])));
        if (populated > 0 && !value(row, legacyField).isBlank()) errors.add(error(row, legacyField, "DIMENSION_FORMAT_CONFLICT", "结构化尺寸与旧尺寸文本不能同时填写", value(row, legacyField)));
    }

    private ProductData product(ImportRow row) { String code = value(row, "product_code"); return new ProductData(null, code, normalize(code), value(row, "product_name"), nullable(row, "product_type"), nullable(row, "description"), status(value(row, "status"), "DRAFT")); }
    private VariantData variant(ImportRow row) { String sku = value(row, "sku_code"), product = value(row, "product_code"); BigDecimal grams = decimal(row,"package_weight_grams");boolean structured=row.data().containsKey("item_length_cm");return new VariantData(null,sku,normalize(sku),product,normalize(product),value(row,"variant_name"),nullable(row,"item_dimensions"),nullable(row,"package_dimensions"),decimal(row,"item_length_cm"),decimal(row,"item_width_cm"),decimal(row,"item_height_cm"),decimal(row,"package_length_cm"),decimal(row,"package_width_cm"),decimal(row,"package_height_cm"),grams,status(value(row,"status"),"DRAFT"),structured); }
    private ProductData findProduct(String code) { return jdbc.sql("SELECT id,product_code,product_code_normalized,COALESCE(NULLIF(product_name,''),name_zh),product_type,description,status FROM product WHERE product_code_normalized=:code").param("code", code).query((rs,n)->new ProductData(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7))).optional().orElse(null); }
    private String productId(String code) { return jdbc.sql("SELECT id FROM product WHERE product_code_normalized=:code").param("code", code).query(String.class).single(); }
    private VariantData findVariant(String sku) { return jdbc.sql("SELECT v.id,v.sku_code,v.sku_code_normalized,p.product_code,p.product_code_normalized,v.variant_name,v.item_dimensions,v.package_dimensions,v.item_length_cm,v.item_width_cm,v.item_height_cm,v.package_length_cm,v.package_width_cm,v.package_height_cm,v.package_weight_grams,v.lifecycle_status FROM product_variant v JOIN product p ON p.id=v.product_id WHERE v.sku_code_normalized=:sku").param("sku", sku).query((rs,n)->new VariantData(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getBigDecimal(9),rs.getBigDecimal(10),rs.getBigDecimal(11),rs.getBigDecimal(12),rs.getBigDecimal(13),rs.getBigDecimal(14),rs.getBigDecimal(15),rs.getString(16),true)).optional().orElse(null); }
    private void insertVariant(String id,String productId,VariantData v){jdbc.sql("INSERT INTO product_variant (id,product_id,sku_code,sku_code_normalized,variant_name,item_dimensions,package_dimensions,item_length_cm,item_width_cm,item_height_cm,package_length_cm,package_width_cm,package_height_cm,package_weight,package_weight_unit,package_weight_grams,status,lifecycle_status,attribute_signature) VALUES (:id,:product,:sku,:normalized,:name,:item,:package,:itemLength,:itemWidth,:itemHeight,:packageLength,:packageWidth,:packageHeight,:grams,CASE WHEN :grams IS NULL THEN NULL ELSE 'G' END,:grams,:legacy,:status,:signature)").param("id",id).param("product",productId).param("sku",v.sku()).param("normalized",v.normalizedSku()).param("name",v.name()).param("item",v.itemDimensions()).param("package",v.packageDimensions()).param("itemLength",v.itemLength()).param("itemWidth",v.itemWidth()).param("itemHeight",v.itemHeight()).param("packageLength",v.packageLength()).param("packageWidth",v.packageWidth()).param("packageHeight",v.packageHeight()).param("grams",v.grams()).param("legacy",legacyStatus(v.status())).param("status",v.status()).param("signature",UUID.randomUUID().toString()).update();}
    private void updateVariant(String id,String productId,VariantData v){jdbc.sql("UPDATE product_variant SET product_id=:product,sku_code=:sku,sku_code_normalized=:normalized,variant_name=:name,item_dimensions=:item,package_dimensions=:package,item_length_cm=CASE WHEN :structured THEN :itemLength ELSE item_length_cm END,item_width_cm=CASE WHEN :structured THEN :itemWidth ELSE item_width_cm END,item_height_cm=CASE WHEN :structured THEN :itemHeight ELSE item_height_cm END,package_length_cm=CASE WHEN :structured THEN :packageLength ELSE package_length_cm END,package_width_cm=CASE WHEN :structured THEN :packageWidth ELSE package_width_cm END,package_height_cm=CASE WHEN :structured THEN :packageHeight ELSE package_height_cm END,package_weight=:grams,package_weight_unit=CASE WHEN :grams IS NULL THEN NULL ELSE 'G' END,package_weight_grams=:grams,status=:legacy,lifecycle_status=:status,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id").param("id",id).param("product",productId).param("sku",v.sku()).param("normalized",v.normalizedSku()).param("name",v.name()).param("item",v.itemDimensions()).param("package",v.packageDimensions()).param("structured",v.structuredPresent()).param("itemLength",v.itemLength()).param("itemWidth",v.itemWidth()).param("itemHeight",v.itemHeight()).param("packageLength",v.packageLength()).param("packageWidth",v.packageWidth()).param("packageHeight",v.packageHeight()).param("grams",v.grams()).param("legacy",legacyStatus(v.status())).param("status",v.status()).update();}
    private String legacyStatus(String status) { return "DISABLED".equals(status) ? "DISABLED" : "ACTIVE"; }
    private String value(ImportRow row, String field) { return row.data().getOrDefault(field, "").trim(); }
    private String nullable(ImportRow row, String field) { String value = value(row, field); return value.isBlank() ? null : value; }
    private BigDecimal decimal(ImportRow row,String field){String value=value(row,field);return value.isBlank()?null:new BigDecimal(value);}
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String status(String value, String defaultValue) { String normalized = normalize(value); return normalized.isBlank() ? defaultValue : normalized; }
    private void requiredCode(List<Error> errors, ImportRow row, String field, String value) { if (!CODE.matcher(value).matches()) errors.add(error(row, field, "CODE_INVALID", "编码必须为 1..64 位字母、数字、下划线或连字符，且以字母或数字开头", value)); }
    private void required(List<Error> errors, ImportRow row, String field, String value, int max) { if (value.isBlank() || value.length() > max) errors.add(error(row, field, "FIELD_REQUIRED_OR_TOO_LONG", "字段必填且长度不得超过 " + max, value)); }
    private void optional(List<Error> errors, ImportRow row, String field, String value, int max) { if (value.length() > max) errors.add(error(row, field, "FIELD_TOO_LONG", "字段长度不得超过 " + max, value)); }
    private void enumeration(List<Error> errors, ImportRow row, String field, String value, String defaultValue) { String normalized = value.isBlank() ? defaultValue : normalize(value); if (!STATUSES.contains(normalized)) errors.add(error(row, field, "ENUM_INVALID", "枚举值不受支持", value)); }
    private Error error(ImportRow row, String field, String code, String reason, String raw) { return new Error(row.sheet(), row.rowNumber(), field, code, reason, raw); }
    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor,String action,String resource,String id,Map<String,?> before,Map<String,?> after){return new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,resource,id,true,before,after,"ERP-021-003 模板 2.0 原子导入");}
    private Map<String,Object> productSnapshot(ProductData p){Map<String,Object> m=new LinkedHashMap<>();m.put("productCode",p.code());m.put("productName",p.name());m.put("status",p.status());return m;}
    private Map<String,Object> variantSnapshot(VariantData v){Map<String,Object> m=new LinkedHashMap<>();m.put("skuCode",v.sku());m.put("productCode",v.productCode());m.put("status",v.status());m.put("itemDimensionsCm",java.util.Arrays.asList(v.itemLength(),v.itemWidth(),v.itemHeight()));m.put("packageDimensionsCm",java.util.Arrays.asList(v.packageLength(),v.packageWidth(),v.packageHeight()));m.put("packageWeightGrams",v.grams());return m;}
    private record ProductData(String id,String code,String normalizedCode,String name,String type,String description,String status){boolean sameBusinessFields(ProductData v){return Objects.equals(code,v.code)&&Objects.equals(name,v.name)&&Objects.equals(type,v.type)&&Objects.equals(description,v.description)&&Objects.equals(status,v.status);}}
    private record VariantData(String id,String sku,String normalizedSku,String productCode,String normalizedProductCode,String name,String itemDimensions,String packageDimensions,BigDecimal itemLength,BigDecimal itemWidth,BigDecimal itemHeight,BigDecimal packageLength,BigDecimal packageWidth,BigDecimal packageHeight,BigDecimal grams,String status,boolean structuredPresent){boolean sameBusinessFields(VariantData v){return Objects.equals(sku,v.sku)&&Objects.equals(normalizedProductCode,v.normalizedProductCode)&&Objects.equals(name,v.name)&&Objects.equals(itemDimensions,v.itemDimensions)&&Objects.equals(packageDimensions,v.packageDimensions)&&(!v.structuredPresent||(decimalEquals(itemLength,v.itemLength)&&decimalEquals(itemWidth,v.itemWidth)&&decimalEquals(itemHeight,v.itemHeight)&&decimalEquals(packageLength,v.packageLength)&&decimalEquals(packageWidth,v.packageWidth)&&decimalEquals(packageHeight,v.packageHeight)))&&decimalEquals(grams,v.grams)&&Objects.equals(status,v.status);}private static boolean decimalEquals(BigDecimal left,BigDecimal right){return left==null?right==null:right!=null&&left.compareTo(right)==0;}}
}
