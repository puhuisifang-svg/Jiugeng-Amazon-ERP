package com.jiugeng.erp.product;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
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

@Service
class ProductImportService implements ProductImportGateway {
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Pattern BARCODE = Pattern.compile("(?:[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})");
    private static final Set<String> PRODUCT_STATUS = Set.of("DRAFT", "ACTIVE", "INACTIVE");
    private static final Set<String> VARIANT_STATUS = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> WEIGHT_UNITS = Set.of("G", "KG", "OZ", "LB");

    private final JdbcClient jdbc;
    private final AuditTrail audit;

    ProductImportService(JdbcClient jdbc, AuditTrail audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public Validation validateProducts(List<ImportRow> rows) {
        List<Error> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (ImportRow row : rows) {
            if (!"导入模板".equals(row.sheet())) continue;
            String code = value(row, "product_code");
            requiredCode(errors, row, "product_code", code);
            required(errors, row, "name_zh", value(row, "name_zh"), 200);
            optional(errors, row, "name_en", value(row, "name_en"), 200);
            optional(errors, row, "brand", value(row, "brand"), 100);
            optional(errors, row, "internal_category", value(row, "internal_category"), 100);
            optional(errors, row, "product_type", value(row, "product_type"), 100);
            optional(errors, row, "description", value(row, "description"), 1000);
            enumeration(errors, row, "status", value(row, "status"), PRODUCT_STATUS, "DRAFT");
            if (!code.isBlank() && !codes.add(normalize(code))) {
                errors.add(error(row, "product_code", "PRODUCT_CODE_DUPLICATED_IN_FILE", "文件内产品编码重复（忽略大小写）", code));
            }
        }
        return new Validation(List.copyOf(errors));
    }

    @Override
    public Validation validateVariants(List<ImportRow> rows) {
        List<Error> errors = new ArrayList<>();
        List<ImportRow> variants = rows.stream().filter(row -> "variants_导入模板".equals(row.sheet())).toList();
        List<ImportRow> attributes = rows.stream().filter(row -> "attributes_导入模板".equals(row.sheet())).toList();
        Set<String> skus = new HashSet<>();
        Set<String> barcodes = new HashSet<>();
        Map<String, String> productBySku = new LinkedHashMap<>();
        for (ImportRow row : variants) {
            String sku = value(row, "sku_code");
            String product = value(row, "product_code");
            requiredCode(errors, row, "sku_code", sku);
            requiredCode(errors, row, "product_code", product);
            required(errors, row, "variant_name", value(row, "variant_name"), 200);
            optional(errors, row, "material", value(row, "material"), 200);
            optional(errors, row, "specification", value(row, "specification"), 300);
            optional(errors, row, "item_dimensions", value(row, "item_dimensions"), 200);
            optional(errors, row, "package_dimensions", value(row, "package_dimensions"), 200);
            enumeration(errors, row, "status", value(row, "status"), VARIANT_STATUS, "ACTIVE");
            if (!sku.isBlank() && !skus.add(normalize(sku))) {
                errors.add(error(row, "sku_code", "SKU_CODE_DUPLICATED_IN_FILE", "文件内 SKU 编码重复（忽略大小写）", sku));
            }
            if (!sku.isBlank()) productBySku.put(normalize(sku), normalize(product));
            String barcode = value(row, "barcode");
            if (!barcode.isBlank()) {
                if (!BARCODE.matcher(barcode).matches()) errors.add(error(row, "barcode", "BARCODE_INVALID", "条码必须为 8、12、13 或 14 位数字", barcode));
                else if (!barcodes.add(barcode)) errors.add(error(row, "barcode", "BARCODE_DUPLICATED_IN_FILE", "文件内条码重复", barcode));
            }
            validateWeight(errors, row);
        }
        Set<String> attributeKeys = new HashSet<>();
        Set<String> orders = new HashSet<>();
        Map<String, List<ImportRow>> attributesBySku = new HashMap<>();
        for (ImportRow row : attributes) {
            String sku = value(row, "sku_code"); String code = value(row, "attribute_code");
            requiredCode(errors, row, "sku_code", sku); requiredCode(errors, row, "attribute_code", code);
            required(errors, row, "attribute_value", value(row, "attribute_value"), 300);
            Integer order = integer(value(row, "display_order"));
            if (order == null || order < 1) errors.add(error(row, "display_order", "ATTRIBUTE_ORDER_INVALID", "展示顺序必须为不小于 1 的整数", value(row, "display_order")));
            String normalizedSku = normalize(sku);
            if (!productBySku.containsKey(normalizedSku)) errors.add(error(row, "sku_code", "ATTRIBUTE_SKU_NOT_IN_FILE", "属性必须引用本文件 variants 工作表中的 SKU", sku));
            if (!attributeKeys.add(normalizedSku + "\u001f" + normalize(code))) errors.add(error(row, "attribute_code", "ATTRIBUTE_CODE_DUPLICATED", "同一 SKU 属性编码重复（忽略大小写）", code));
            if (order != null && !orders.add(normalizedSku + "\u001f" + order)) errors.add(error(row, "display_order", "ATTRIBUTE_ORDER_DUPLICATED", "同一 SKU 展示顺序重复", String.valueOf(order)));
            attributesBySku.computeIfAbsent(normalizedSku, ignored -> new ArrayList<>()).add(row);
        }
        for (ImportRow row : variants) {
            String sku = normalize(value(row, "sku_code"));
            if (!sku.isBlank() && !attributesBySku.containsKey(sku)) errors.add(error(row, "sku_code", "VARIANT_ATTRIBUTES_REQUIRED", "每个 SKU 至少需要一条动态属性", value(row, "sku_code")));
        }
        validateDatabaseReferences(variants, attributesBySku, errors);
        return new Validation(List.copyOf(errors));
    }

    @Override
    public Result commitProducts(List<ImportRow> rows, AuthenticatedActor actor) {
        Validation validation = validateProducts(rows);
        if (!validation.valid()) throw new IllegalArgumentException("Products must be valid before commit");
        int created = 0, updated = 0, unchanged = 0;
        for (ImportRow row : rows) {
            if (!"导入模板".equals(row.sheet())) continue;
            ProductData next = product(row);
            ProductData current = findProduct(next.normalizedCode());
            if (current == null) {
                String id = UUID.randomUUID().toString();
                jdbc.sql("""
                        INSERT INTO product (id,product_code,product_code_normalized,name_zh,name_en,brand,internal_category,
                            product_type,description,status,created_by,updated_by)
                        VALUES (:id,:code,:normalized,:nameZh,:nameEn,:brand,:category,:type,:description,:status,:actor,:actor)
                        """).params(Map.of("id", id, "code", next.code(), "normalized", next.normalizedCode(),
                                "nameZh", next.nameZh(), "status", next.status(), "actor", actor.userId()))
                        .param("nameEn", next.nameEn()).param("brand", next.brand()).param("category", next.category())
                        .param("type", next.type()).param("description", next.description()).update();
                audit.record(operation(actor, "PRODUCT_IMPORT_CREATE", "PRODUCT", id, null, productSnapshot(next)));
                created++;
            } else if (current.sameBusinessFields(next)) {
                unchanged++;
            } else {
                jdbc.sql("""
                        UPDATE product SET product_code=:code,name_zh=:nameZh,name_en=:nameEn,brand=:brand,
                            internal_category=:category,product_type=:type,description=:description,status=:status,
                            updated_by=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id
                        """).param("id", current.id()).param("code", next.code()).param("nameZh", next.nameZh())
                        .param("nameEn", next.nameEn()).param("brand", next.brand()).param("category", next.category())
                        .param("type", next.type()).param("description", next.description()).param("status", next.status())
                        .param("actor", actor.userId()).update();
                if ("DISABLED".equals(next.status())) jdbc.sql("UPDATE product_variant SET status='DISABLED',updated_at=CURRENT_TIMESTAMP WHERE product_id=:id")
                        .param("id", current.id()).update();
                audit.record(operation(actor, "PRODUCT_IMPORT_UPDATE", "PRODUCT", current.id(), productSnapshot(current), productSnapshot(next)));
                updated++;
            }
        }
        return new Result(created, updated, unchanged);
    }

    @Override
    public Result commitVariants(List<ImportRow> rows, AuthenticatedActor actor) {
        Validation validation = validateVariants(rows);
        if (!validation.valid()) throw new IllegalArgumentException("Variants must be valid before commit");
        Map<String, List<AttributeData>> attributes = attributes(rows);
        List<VariantData> nextRows = rows.stream().filter(row -> "variants_导入模板".equals(row.sheet()))
                .map(row -> variant(row, attributes.get(normalize(value(row, "sku_code"))))).toList();
        List<String> existingIds = nextRows.stream().map(row -> findVariant(row.normalizedSku())).filter(Objects::nonNull)
                .map(VariantData::id).toList();
        for (String id : existingIds) jdbc.sql("UPDATE product_variant SET attribute_signature=:signature WHERE id=:id")
                .param("signature", "__IMPORTING__" + id).param("id", id).update();
        int created = 0, updated = 0, unchanged = 0;
        for (VariantData next : nextRows) {
            VariantData current = findVariant(next.normalizedSku());
            String productId = productId(next.normalizedProductCode());
            if (current == null) {
                String id = UUID.randomUUID().toString();
                insertVariant(id, productId, next);
                replaceAttributes(id, next.attributes());
                audit.record(operation(actor, "VARIANT_IMPORT_CREATE", "PRODUCT_VARIANT", id, null, variantSnapshot(next)));
                created++;
            } else if (current.sameBusinessFields(next) && current.attributes().equals(next.attributes())) {
                jdbc.sql("UPDATE product_variant SET attribute_signature=:signature WHERE id=:id")
                        .param("signature", next.signature()).param("id", current.id()).update();
                unchanged++;
            } else {
                updateVariant(current.id(), productId, next);
                replaceAttributes(current.id(), next.attributes());
                audit.record(operation(actor, "VARIANT_IMPORT_UPDATE", "PRODUCT_VARIANT", current.id(), variantSnapshot(current), variantSnapshot(next)));
                updated++;
            }
        }
        return new Result(created, updated, unchanged);
    }

    private void validateDatabaseReferences(List<ImportRow> rows, Map<String, List<ImportRow>> attributes, List<Error> errors) {
        Map<String, String> productIds = new HashMap<>();
        Set<String> importedSkus = rows.stream().map(row -> normalize(value(row, "sku_code"))).collect(java.util.stream.Collectors.toSet());
        for (ImportRow row : rows) {
            String productCode = normalize(value(row, "product_code"));
            var found = jdbc.sql("SELECT id,status FROM product WHERE product_code_normalized=:code")
                    .param("code", productCode).query((rs, n) -> Map.entry(rs.getString(1), rs.getString(2))).optional();
            if (found.isEmpty() || "DISABLED".equals(found.get().getValue())) {
                errors.add(error(row, "product_code", "PRODUCT_REFERENCE_INVALID", "产品不存在或已停用", value(row, "product_code")));
            } else productIds.put(productCode, found.get().getKey());
            VariantData existing = findVariant(normalize(value(row, "sku_code")));
            if (existing != null && !existing.normalizedProductCode().equals(productCode)) {
                errors.add(error(row, "product_code", "SKU_PRODUCT_CONFLICT", "现有 SKU 属于其他产品", value(row, "product_code")));
            }
            String barcode = value(row, "barcode");
            if (!barcode.isBlank()) {
                String currentId = existing == null ? "" : existing.id();
                int count = jdbc.sql("SELECT COUNT(*) FROM product_variant WHERE barcode_normalized=:barcode AND id<>:id")
                        .param("barcode", normalize(barcode)).param("id", currentId).query(Integer.class).single();
                if (count > 0) errors.add(error(row, "barcode", "BARCODE_EXISTS", "条码已被其他 SKU 使用", barcode));
            }
        }
        Map<String, Set<String>> signatures = new HashMap<>();
        for (ImportRow row : rows) {
            String sku = normalize(value(row, "sku_code"));
            List<AttributeData> values = attributes.getOrDefault(sku, List.of()).stream().map(this::attribute).toList();
            if (values.isEmpty()) continue;
            String signature = signature(values); String productCode = normalize(value(row, "product_code"));
            String productId = productIds.get(productCode);
            if (!signatures.computeIfAbsent(productCode, ignored -> new HashSet<>()).add(signature)) {
                errors.add(error(row, "sku_code", "VARIANT_COMBINATION_DUPLICATED_IN_FILE", "同一产品在文件内存在重复属性组合", value(row, "sku_code")));
            }
            if (productId != null) {
                VariantData current = findVariant(sku); String currentId = current == null ? "" : current.id();
                List<String> conflictingSkus = jdbc.sql("SELECT sku_code_normalized FROM product_variant WHERE product_id=:product AND attribute_signature=:signature AND id<>:id")
                        .param("product", productId).param("signature", signature).param("id", currentId).query(String.class).list();
                if (conflictingSkus.stream().anyMatch(existingSku -> !importedSkus.contains(existingSku)))
                    errors.add(error(row, "sku_code", "VARIANT_COMBINATION_EXISTS", "同一产品已存在相同属性组合", value(row, "sku_code")));
            }
        }
    }

    private void validateWeight(List<Error> errors, ImportRow row) {
        String raw = value(row, "package_weight"); String unit = value(row, "package_weight_unit").toUpperCase(Locale.ROOT);
        if (raw.isBlank() && !unit.isBlank()) errors.add(error(row, "package_weight_unit", "WEIGHT_UNIT_WITHOUT_VALUE", "无重量时单位必须为空", unit));
        if (!raw.isBlank()) {
            try { if (new BigDecimal(raw).signum() <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException exception) { errors.add(error(row, "package_weight", "PACKAGE_WEIGHT_INVALID", "包装重量必须大于 0", raw)); }
            if (!WEIGHT_UNITS.contains(unit)) errors.add(error(row, "package_weight_unit", "PACKAGE_WEIGHT_UNIT_INVALID", "重量单位必须为 G、KG、OZ 或 LB", unit));
        }
    }

    private Map<String, List<AttributeData>> attributes(List<ImportRow> rows) {
        Map<String, List<AttributeData>> values = new HashMap<>();
        rows.stream().filter(row -> "attributes_导入模板".equals(row.sheet())).forEach(row ->
                values.computeIfAbsent(normalize(value(row, "sku_code")), ignored -> new ArrayList<>()).add(attribute(row)));
        values.values().forEach(list -> list.sort(Comparator.comparingInt(AttributeData::order).thenComparing(AttributeData::normalizedCode)));
        return values;
    }

    private ProductData product(ImportRow row) {
        String code = value(row, "product_code");
        return new ProductData(null, code, normalize(code), value(row, "name_zh"), nullable(row, "name_en"),
                nullable(row, "brand"), nullable(row, "internal_category"), nullable(row, "product_type"),
                nullable(row, "description"), productStatus(value(row, "status")));
    }
    private VariantData variant(ImportRow row, List<AttributeData> attributes) {
        String sku = value(row, "sku_code"); String product = value(row, "product_code"); String barcode = nullable(row, "barcode");
        BigDecimal weight = value(row, "package_weight").isBlank() ? null : new BigDecimal(value(row, "package_weight"));
        return new VariantData(null, sku, normalize(sku), product, normalize(product), value(row, "variant_name"), barcode,
                barcode == null ? null : normalize(barcode), nullable(row, "material"), nullable(row, "specification"),
                nullable(row, "item_dimensions"), nullable(row, "package_dimensions"), weight,
                nullable(row, "package_weight_unit"), variantStatus(value(row, "status")), List.copyOf(attributes), signature(attributes));
    }
    private AttributeData attribute(ImportRow row) { String code=value(row,"attribute_code"); return new AttributeData(code,normalize(code),value(row,"attribute_value"),Integer.parseInt(value(row,"display_order"))); }

    private ProductData findProduct(String code) {
        return jdbc.sql("SELECT id,product_code,product_code_normalized,name_zh,name_en,brand,internal_category,product_type,description,status FROM product WHERE product_code_normalized=:code")
                .param("code", code).query((rs,n)->new ProductData(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10))).optional().orElse(null);
    }
    private String productId(String code) { return jdbc.sql("SELECT id FROM product WHERE product_code_normalized=:code").param("code",code).query(String.class).single(); }
    private VariantData findVariant(String sku) {
        var row=jdbc.sql("""
                SELECT v.id,v.sku_code,v.sku_code_normalized,p.product_code,p.product_code_normalized,v.variant_name,
                    v.barcode,v.barcode_normalized,v.material,v.specification,v.item_dimensions,v.package_dimensions,
                    v.package_weight,v.package_weight_unit,v.status,v.attribute_signature
                FROM product_variant v JOIN product p ON p.id=v.product_id WHERE v.sku_code_normalized=:sku
                """).param("sku",sku).query((rs,n)->new VariantData(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11),rs.getString(12),rs.getBigDecimal(13),rs.getString(14),rs.getString(15),List.of(),rs.getString(16))).optional().orElse(null);
        if(row==null)return null;
        List<AttributeData> attrs=jdbc.sql("SELECT attribute_code,attribute_code_normalized,attribute_value,display_order FROM variant_attribute WHERE variant_id=:id ORDER BY display_order,attribute_code_normalized")
                .param("id",row.id()).query((rs,n)->new AttributeData(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4))).list();
        return row.withAttributes(attrs);
    }

    private void insertVariant(String id,String productId,VariantData v){jdbc.sql("""
            INSERT INTO product_variant (id,product_id,sku_code,sku_code_normalized,variant_name,barcode,barcode_normalized,
                material,specification,item_dimensions,package_dimensions,package_weight,package_weight_unit,status,attribute_signature)
            VALUES (:id,:product,:sku,:normalized,:name,:barcode,:normalizedBarcode,:material,:specification,:itemDimensions,
                :packageDimensions,:weight,:unit,:status,:signature)
            """).param("id",id).param("product",productId).param("sku",v.sku()).param("normalized",v.normalizedSku()).param("name",v.name())
            .param("barcode",v.barcode()).param("normalizedBarcode",v.normalizedBarcode()).param("material",v.material()).param("specification",v.specification())
            .param("itemDimensions",v.itemDimensions()).param("packageDimensions",v.packageDimensions()).param("weight",v.weight()).param("unit",v.weightUnit())
            .param("status",v.status()).param("signature",v.signature()).update();}
    private void updateVariant(String id,String productId,VariantData v){jdbc.sql("""
            UPDATE product_variant SET product_id=:product,sku_code=:sku,variant_name=:name,barcode=:barcode,
                barcode_normalized=:normalizedBarcode,material=:material,specification=:specification,item_dimensions=:itemDimensions,
                package_dimensions=:packageDimensions,package_weight=:weight,package_weight_unit=:unit,status=:status,
                attribute_signature=:signature,updated_at=CURRENT_TIMESTAMP WHERE id=:id
            """).param("id",id).param("product",productId).param("sku",v.sku()).param("name",v.name()).param("barcode",v.barcode())
            .param("normalizedBarcode",v.normalizedBarcode()).param("material",v.material()).param("specification",v.specification())
            .param("itemDimensions",v.itemDimensions()).param("packageDimensions",v.packageDimensions()).param("weight",v.weight()).param("unit",v.weightUnit())
            .param("status",v.status()).param("signature",v.signature()).update();}
    private void replaceAttributes(String id,List<AttributeData> attrs){jdbc.sql("DELETE FROM variant_attribute WHERE variant_id=:id").param("id",id).update();for(AttributeData a:attrs)jdbc.sql("INSERT INTO variant_attribute (variant_id,attribute_code,attribute_code_normalized,attribute_value,display_order) VALUES (:id,:code,:normalized,:value,:order)").param("id",id).param("code",a.code()).param("normalized",a.normalizedCode()).param("value",a.value()).param("order",a.order()).update();}

    private String signature(List<AttributeData> attrs){String canonical=attrs.stream().sorted(Comparator.comparing(AttributeData::normalizedCode)).map(a->a.normalizedCode()+"\u001f"+normalize(a.value())).reduce((a,b)->a+"|"+b).orElse("");try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor,String action,String resource,String id,Map<String,?> before,Map<String,?> after){return new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,resource,id,true,before,after,"ERP-015 原子导入");}
    private Map<String,Object> productSnapshot(ProductData p){Map<String,Object> m=new LinkedHashMap<>();m.put("productCode",p.code());m.put("nameZh",p.nameZh());m.put("status",p.status());return m;}
    private Map<String,Object> variantSnapshot(VariantData v){Map<String,Object> m=new LinkedHashMap<>();m.put("skuCode",v.sku());m.put("productCode",v.productCode());m.put("status",v.status());m.put("attributeCodes",v.attributes().stream().map(AttributeData::code).toList());return m;}
    private String value(ImportRow row,String field){return row.data().getOrDefault(field,"").trim();}
    private String nullable(ImportRow row,String field){String v=value(row,field);return v.isBlank()?null:v;}
    private String normalize(String value){return value==null?"":value.trim().toUpperCase(Locale.ROOT);}
    private String productStatus(String value){String v=normalize(value);return v.isBlank()?"DRAFT":("INACTIVE".equals(v)?"DISABLED":v);}
    private String variantStatus(String value){String v=normalize(value);return v.isBlank()?"ACTIVE":("INACTIVE".equals(v)?"DISABLED":v);}
    private Integer integer(String value){try{return Integer.valueOf(value);}catch(Exception e){return null;}}
    private void requiredCode(List<Error> errors,ImportRow row,String field,String value){if(!CODE.matcher(value).matches())errors.add(error(row,field,"CODE_INVALID","编码必须为 1..64 位字母、数字、下划线或连字符，且以字母或数字开头",value));}
    private void required(List<Error> errors,ImportRow row,String field,String value,int max){if(value.isBlank()||value.length()>max)errors.add(error(row,field,"FIELD_REQUIRED_OR_TOO_LONG","字段必填且长度不得超过 "+max,value));}
    private void optional(List<Error> errors,ImportRow row,String field,String value,int max){if(value.length()>max)errors.add(error(row,field,"FIELD_TOO_LONG","字段长度不得超过 "+max,value));}
    private void enumeration(List<Error> errors,ImportRow row,String field,String value,Set<String> allowed,String defaultValue){String v=value.isBlank()?defaultValue:normalize(value);if(!allowed.contains(v))errors.add(error(row,field,"ENUM_INVALID","枚举值不受支持",value));}
    private Error error(ImportRow row,String field,String code,String reason,String raw){return new Error(row.sheet(),row.rowNumber(),field,code,reason,raw);}

    private record ProductData(String id,String code,String normalizedCode,String nameZh,String nameEn,String brand,String category,String type,String description,String status){boolean sameBusinessFields(ProductData v){return Objects.equals(code,v.code)&&Objects.equals(nameZh,v.nameZh)&&Objects.equals(nameEn,v.nameEn)&&Objects.equals(brand,v.brand)&&Objects.equals(category,v.category)&&Objects.equals(type,v.type)&&Objects.equals(description,v.description)&&Objects.equals(status,v.status);}}
    private record AttributeData(String code,String normalizedCode,String value,int order){}
    private record VariantData(String id,String sku,String normalizedSku,String productCode,String normalizedProductCode,String name,String barcode,String normalizedBarcode,String material,String specification,String itemDimensions,String packageDimensions,BigDecimal weight,String weightUnit,String status,List<AttributeData> attributes,String signature){VariantData withAttributes(List<AttributeData> a){return new VariantData(id,sku,normalizedSku,productCode,normalizedProductCode,name,barcode,normalizedBarcode,material,specification,itemDimensions,packageDimensions,weight,weightUnit,status,List.copyOf(a),signature);}boolean sameBusinessFields(VariantData v){return Objects.equals(sku,v.sku)&&Objects.equals(normalizedProductCode,v.normalizedProductCode)&&Objects.equals(name,v.name)&&Objects.equals(barcode,v.barcode)&&Objects.equals(material,v.material)&&Objects.equals(specification,v.specification)&&Objects.equals(itemDimensions,v.itemDimensions)&&Objects.equals(packageDimensions,v.packageDimensions)&&decimalEquals(weight,v.weight)&&Objects.equals(weightUnit,v.weightUnit)&&Objects.equals(status,v.status);}private static boolean decimalEquals(BigDecimal left,BigDecimal right){return left==null?right==null:right!=null&&left.compareTo(right)==0;}}
}
