package com.jiugeng.erp.materialinventory;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.net.URI;
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

@Service
class MaterialImportService implements MaterialImportGateway {
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private static final Set<String> MATERIAL_TYPES = Set.of("LABEL", "BOX", "BAG", "MANUAL", "INSERT", "OTHER");
    private static final Set<String> UNITS = Set.of("PCS", "SET", "ROLL", "SHEET", "M", "KG");
    private static final Set<String> MATERIAL_STATUSES = Set.of("ACTIVE", "INACTIVE");
    private static final Set<String> ENABLED_VALUES = Set.of("TRUE", "FALSE");

    private final JdbcClient jdbc;
    private final AuditTrail audit;

    MaterialImportService(JdbcClient jdbc, AuditTrail audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public Validation validateMaterials(List<ImportRow> rows) {
        List<Error> errors = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        for (ImportRow row : dataRows(rows)) {
            String code = value(row, "material_code");
            requiredCode(errors, row, "material_code", code);
            required(errors, row, "material_name", value(row, "material_name"), 200);
            enumeration(errors, row, "material_type", value(row, "material_type"), MATERIAL_TYPES);
            optional(errors, row, "specification", value(row, "specification"), 500);
            enumeration(errors, row, "base_unit", value(row, "base_unit"), UNITS);
            image(errors, row, value(row, "image_url"));
            enumeration(errors, row, "status", value(row, "status"), MATERIAL_STATUSES);
            optional(errors, row, "remark", value(row, "remark"), 1000);
            if (!code.isBlank() && !codes.add(normalize(code))) {
                errors.add(error(row, "material_code", "MATERIAL_CODE_DUPLICATED_IN_FILE",
                        "文件内辅料编码重复（忽略大小写）", code));
            }
        }
        return new Validation(List.copyOf(errors));
    }

    @Override
    public Validation validateRelations(List<ImportRow> rows) {
        List<Error> errors = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (ImportRow row : dataRows(rows)) {
            String sku = value(row, "sku_code");
            String material = value(row, "material_code");
            requiredCode(errors, row, "sku_code", sku);
            requiredCode(errors, row, "material_code", material);
            decimal(errors, row, "standard_quantity", value(row, "standard_quantity"), true, 12, 4);
            enumeration(errors, row, "unit", value(row, "unit"), UNITS);
            decimal(errors, row, "loss_rate", value(row, "loss_rate"), false, 7, 4);
            enumeration(errors, row, "enabled", value(row, "enabled"), ENABLED_VALUES);
            optional(errors, row, "remark", value(row, "remark"), 1000);
            String key = normalize(sku) + "\u001f" + normalize(material);
            if (!sku.isBlank() && !material.isBlank() && !keys.add(key)) {
                errors.add(error(row, "material_code", "MATERIAL_RELATION_DUPLICATED_IN_FILE",
                        "文件内同一 SKU 与辅料关系重复（忽略大小写）", material));
            }
            validateReferences(row, errors);
        }
        return new Validation(List.copyOf(errors));
    }

    @Override
    public Result commitMaterials(List<ImportRow> rows, AuthenticatedActor actor) {
        Validation validation = validateMaterials(rows);
        if (!validation.valid()) throw new IllegalArgumentException("Materials must be valid before commit");
        int created = 0, updated = 0, unchanged = 0;
        for (ImportRow row : dataRows(rows)) {
            MaterialData next = material(row);
            MaterialData current = findMaterial(next.normalizedCode());
            if (current == null) {
                String id = UUID.randomUUID().toString();
                jdbc.sql("""
                        INSERT INTO auxiliary_material (id,material_code,material_code_normalized,name,material_type,
                            specification,unit,image_url,remark,status,created_by,updated_by)
                        VALUES (:id,:code,:normalized,:name,:type,:specification,:unit,:imageUrl,:remark,:status,:actor,:actor)
                        """).param("id", id).param("code", next.code()).param("normalized", next.normalizedCode())
                        .param("name", next.name()).param("type", next.type()).param("specification", next.specification())
                        .param("unit", next.unit()).param("imageUrl", next.imageUrl()).param("remark", next.remark())
                        .param("status", next.status()).param("actor", actor.userId()).update();
                audit.record(operation(actor, "MATERIAL_IMPORT_CREATE", "AUXILIARY_MATERIAL", id, null,
                        materialSnapshot(next)));
                created++;
            } else if (current.sameBusinessFields(next)) {
                unchanged++;
            } else {
                jdbc.sql("""
                        UPDATE auxiliary_material SET name=:name,material_type=:type,specification=:specification,
                            unit=:unit,image_url=:imageUrl,remark=:remark,status=:status,updated_by=:actor,
                            updated_at=CURRENT_TIMESTAMP WHERE id=:id
                        """).param("id", current.id()).param("name", next.name()).param("type", next.type())
                        .param("specification", next.specification()).param("unit", next.unit())
                        .param("imageUrl", next.imageUrl()).param("remark", next.remark())
                        .param("status", next.status()).param("actor", actor.userId()).update();
                audit.record(operation(actor, "MATERIAL_IMPORT_UPDATE", "AUXILIARY_MATERIAL", current.id(),
                        materialSnapshot(current), materialSnapshot(next.withCode(current.code()))));
                updated++;
            }
        }
        return new Result(created, updated, unchanged);
    }

    @Override
    public Result commitRelations(List<ImportRow> rows, AuthenticatedActor actor) {
        Validation validation = validateRelations(rows);
        if (!validation.valid()) throw new IllegalArgumentException("Material relations must be valid before commit");
        int created = 0, updated = 0, unchanged = 0;
        for (ImportRow row : dataRows(rows)) {
            RelationData next = relation(row);
            RelationData current = findRelation(next.normalizedSku(), next.normalizedMaterialCode());
            Reference reference = reference(next.normalizedSku(), next.normalizedMaterialCode());
            if (current == null) {
                String id = UUID.randomUUID().toString();
                jdbc.sql("""
                        INSERT INTO product_auxiliary_material (id,product_id,variant_id,auxiliary_material_id,
                            standard_quantity,unit,loss_rate,remark,enabled,created_by,updated_by)
                        VALUES (:id,:product,:variant,:material,:quantity,:unit,:lossRate,:remark,:enabled,:actor,:actor)
                        """).param("id", id).param("product", reference.productId()).param("variant", reference.variantId())
                        .param("material", reference.materialId()).param("quantity", next.quantity()).param("unit", next.unit())
                        .param("lossRate", next.lossRate()).param("remark", next.remark()).param("enabled", next.enabled())
                        .param("actor", actor.userId()).update();
                audit.record(operation(actor, "PRODUCT_MATERIAL_IMPORT_CREATE", "PRODUCT_AUXILIARY_MATERIAL", id,
                        null, relationSnapshot(next)));
                created++;
            } else if (current.sameBusinessFields(next)) {
                unchanged++;
            } else {
                jdbc.sql("""
                        UPDATE product_auxiliary_material SET standard_quantity=:quantity,unit=:unit,
                            loss_rate=:lossRate,remark=:remark,enabled=:enabled,updated_by=:actor,
                            updated_at=CURRENT_TIMESTAMP WHERE id=:id
                        """).param("id", current.id()).param("quantity", next.quantity()).param("unit", next.unit())
                        .param("lossRate", next.lossRate()).param("remark", next.remark()).param("enabled", next.enabled())
                        .param("actor", actor.userId()).update();
                audit.record(operation(actor, "PRODUCT_MATERIAL_IMPORT_UPDATE", "PRODUCT_AUXILIARY_MATERIAL",
                        current.id(), relationSnapshot(current), relationSnapshot(next)));
                updated++;
            }
        }
        return new Result(created, updated, unchanged);
    }

    private void validateReferences(ImportRow row, List<Error> errors) {
        String sku = value(row, "sku_code");
        String material = value(row, "material_code");
        if (!CODE.matcher(sku).matches() || !CODE.matcher(material).matches()) return;
        var variant = jdbc.sql("""
                SELECT v.id FROM product_variant v JOIN product p ON p.id=v.product_id
                WHERE v.sku_code_normalized=:sku AND v.status='ACTIVE' AND p.status='ACTIVE'
                """).param("sku", normalize(sku)).query(String.class).optional();
        if (variant.isEmpty()) {
            errors.add(error(row, "sku_code", "SKU_REFERENCE_INVALID", "SKU 不存在、已停用或所属产品已停用", sku));
        }
        var materialUnit = jdbc.sql("SELECT unit FROM auxiliary_material WHERE material_code_normalized=:code AND status='ACTIVE'")
                .param("code", normalize(material)).query(String.class).optional();
        if (materialUnit.isEmpty()) {
            errors.add(error(row, "material_code", "MATERIAL_REFERENCE_INVALID", "辅料不存在或已停用", material));
        } else if (!materialUnit.get().equals(normalize(value(row, "unit")))) {
            errors.add(error(row, "unit", "MATERIAL_UNIT_MISMATCH", "用量单位必须等于辅料基础单位", value(row, "unit")));
        }
    }

    private Reference reference(String sku, String material) {
        return jdbc.sql("""
                SELECT p.id,v.id,m.id FROM product_variant v JOIN product p ON p.id=v.product_id
                JOIN auxiliary_material m ON m.material_code_normalized=:material
                WHERE v.sku_code_normalized=:sku AND v.status='ACTIVE' AND p.status='ACTIVE' AND m.status='ACTIVE'
                """).param("sku", sku).param("material", material)
                .query((rs, n) -> new Reference(rs.getString(1), rs.getString(2), rs.getString(3))).single();
    }

    private MaterialData findMaterial(String code) {
        return jdbc.sql("""
                SELECT id,material_code,material_code_normalized,name,material_type,specification,unit,image_url,remark,status
                FROM auxiliary_material WHERE material_code_normalized=:code
                """).param("code", code).query((rs, n) -> new MaterialData(rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                        rs.getString(8), rs.getString(9), rs.getString(10))).optional().orElse(null);
    }

    private RelationData findRelation(String sku, String material) {
        return jdbc.sql("""
                SELECT r.id,v.sku_code,v.sku_code_normalized,m.material_code,m.material_code_normalized,
                    r.standard_quantity,r.unit,r.loss_rate,r.remark,r.enabled
                FROM product_auxiliary_material r JOIN product_variant v ON v.id=r.variant_id
                JOIN auxiliary_material m ON m.id=r.auxiliary_material_id
                WHERE v.sku_code_normalized=:sku AND m.material_code_normalized=:material
                """).param("sku", sku).param("material", material).query((rs, n) -> new RelationData(rs.getString(1),
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBigDecimal(6),
                        rs.getString(7), rs.getBigDecimal(8), rs.getString(9), rs.getBoolean(10)))
                .optional().orElse(null);
    }

    private MaterialData material(ImportRow row) {
        String code = value(row, "material_code");
        return new MaterialData(null, code, normalize(code), value(row, "material_name"),
                normalize(value(row, "material_type")), nullable(row, "specification"),
                normalize(value(row, "base_unit")), nullable(row, "image_url"), nullable(row, "remark"),
                "INACTIVE".equals(normalize(value(row, "status"))) ? "DISABLED" : "ACTIVE");
    }

    private RelationData relation(ImportRow row) {
        String sku = value(row, "sku_code");
        String material = value(row, "material_code");
        return new RelationData(null, sku, normalize(sku), material, normalize(material),
                new BigDecimal(value(row, "standard_quantity")), normalize(value(row, "unit")),
                new BigDecimal(value(row, "loss_rate")), nullable(row, "remark"),
                Boolean.parseBoolean(normalize(value(row, "enabled"))));
    }

    private List<ImportRow> dataRows(List<ImportRow> rows) {
        return rows.stream().filter(row -> "导入模板".equals(row.sheet())).toList();
    }

    private void requiredCode(List<Error> errors, ImportRow row, String field, String raw) {
        if (!CODE.matcher(raw).matches()) errors.add(error(row, field, "CODE_INVALID",
                "编码必须为 1..64 位字母、数字、下划线或连字符，且以字母或数字开头", raw));
    }

    private void required(List<Error> errors, ImportRow row, String field, String raw, int max) {
        if (raw.isBlank() || raw.length() > max) errors.add(error(row, field, "FIELD_REQUIRED_OR_TOO_LONG",
                "字段必填且长度不得超过 " + max, raw));
    }

    private void optional(List<Error> errors, ImportRow row, String field, String raw, int max) {
        if (raw.length() > max) errors.add(error(row, field, "FIELD_TOO_LONG", "字段长度不得超过 " + max, raw));
    }

    private void enumeration(List<Error> errors, ImportRow row, String field, String raw, Set<String> allowed) {
        if (!allowed.contains(normalize(raw))) errors.add(error(row, field, "ENUM_INVALID", "枚举值不受支持", raw));
    }

    private void decimal(List<Error> errors, ImportRow row, String field, String raw, boolean positive,
            int precision, int scale) {
        try {
            BigDecimal value = new BigDecimal(raw);
            boolean range = positive ? value.signum() > 0 : value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0;
            if (!range || value.precision() > precision || Math.max(0, value.scale()) > scale) throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            errors.add(error(row, field, "DECIMAL_INVALID", positive
                    ? "标准用量必须大于 0，且最多 12 位、4 位小数"
                    : "损耗率必须在 0 到 1 之间，且最多 4 位小数", raw));
        }
    }

    private void image(List<Error> errors, ImportRow row, String raw) {
        if (raw.isBlank()) return;
        if (raw.length() > 500) {
            errors.add(error(row, "image_url", "IMAGE_URL_INVALID", "图片 URL 长度不得超过 500", raw));
            return;
        }
        try {
            URI value = URI.create(raw);
            if (!"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null || value.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            errors.add(error(row, "image_url", "IMAGE_URL_INVALID", "图片 URL 必须是无用户信息的 HTTPS 地址", raw));
        }
    }

    private Error error(ImportRow row, String field, String code, String reason, String raw) {
        return new Error(row.sheet(), row.rowNumber(), field, code, reason, raw);
    }

    private String value(ImportRow row, String field) { return row.data().getOrDefault(field, "").trim(); }
    private String nullable(ImportRow row, String field) { String value = value(row, field); return value.isBlank() ? null : value; }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }

    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor, String action, String resource,
            String id, Map<String, ?> before, Map<String, ?> after) {
        return new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(), action,
                resource, id, true, before, after, "ERP-016 原子导入");
    }

    private Map<String, Object> materialSnapshot(MaterialData value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("materialCode", value.code()); result.put("name", value.name());
        result.put("materialType", value.type()); result.put("unit", value.unit()); result.put("status", value.status());
        return result;
    }

    private Map<String, Object> relationSnapshot(RelationData value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skuCode", value.sku()); result.put("materialCode", value.materialCode());
        result.put("standardQuantity", value.quantity()); result.put("unit", value.unit());
        result.put("lossRate", value.lossRate()); result.put("enabled", value.enabled());
        return result;
    }

    private record Reference(String productId, String variantId, String materialId) {}
    private record MaterialData(String id, String code, String normalizedCode, String name, String type,
            String specification, String unit, String imageUrl, String remark, String status) {
        MaterialData withCode(String displayCode) {
            return new MaterialData(id, displayCode, normalizedCode, name, type, specification, unit, imageUrl,
                    remark, status);
        }
        boolean sameBusinessFields(MaterialData value) {
            return Objects.equals(name, value.name) && Objects.equals(type, value.type)
                    && Objects.equals(specification, value.specification) && Objects.equals(unit, value.unit)
                    && Objects.equals(imageUrl, value.imageUrl) && Objects.equals(remark, value.remark)
                    && Objects.equals(status, value.status);
        }
    }
    private record RelationData(String id, String sku, String normalizedSku, String materialCode,
            String normalizedMaterialCode, BigDecimal quantity, String unit, BigDecimal lossRate, String remark,
            boolean enabled) {
        boolean sameBusinessFields(RelationData value) {
            return quantity.compareTo(value.quantity) == 0 && Objects.equals(unit, value.unit)
                    && lossRate.compareTo(value.lossRate) == 0 && Objects.equals(remark, value.remark)
                    && enabled == value.enabled;
        }
    }
}
