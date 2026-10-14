package com.jiugeng.erp.materialinventory;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
class InventoryOpeningImportService implements InventoryOpeningImportGateway {
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");
    private final JdbcClient jdbc;
    private final InventoryOpeningService openings;

    InventoryOpeningImportService(JdbcClient jdbc, InventoryOpeningService openings) {
        this.jdbc = jdbc;
        this.openings = openings;
    }

    @Override
    public Validation validate(List<ImportRow> rows) {
        List<Error> errors = new ArrayList<>();
        Set<String> materialKeys = new HashSet<>();
        for (ImportRow row : dataRows(rows)) {
            String warehouse = value(row, "warehouse_code");
            String material = value(row, "material_code");
            String reference = value(row, "opening_reference");
            required(errors, row, "warehouse_code", warehouse, 32);
            requiredCode(errors, row, "material_code", material);
            decimal(errors, row, "opening_quantity", value(row, "opening_quantity"));
            decimal(errors, row, "safety_stock", value(row, "safety_stock"));
            date(errors, row, value(row, "as_of_date"));
            required(errors, row, "opening_reference", reference, 100);
            optional(errors, row, "remark", value(row, "remark"), 1000);
            String warehouseKey = normalize(warehouse);
            if (!warehouse.isBlank() && !enabledWarehouse(warehouseKey)) {
                errors.add(error(row, "warehouse_code", "WAREHOUSE_REFERENCE_INVALID",
                        "仓库不存在、已停用或不是系统固定单仓", warehouse));
            }
            MaterialRef found = CODE.matcher(material).matches() ? material(normalize(material)) : null;
            if (found == null) {
                errors.add(error(row, "material_code", "MATERIAL_REFERENCE_INVALID", "辅料不存在或已停用", material));
            } else if (found.openingTransactionId() != null && validReplay(row, warehouseKey, reference, found) == false) {
                errors.add(error(row, "material_code", "OPENING_INVENTORY_CONFLICT",
                        "该仓库与辅料已存在不同依据或载荷的期初流水", material));
            }
            String materialKey = warehouseKey + "\u001f" + normalize(material);
            if (!warehouse.isBlank() && !material.isBlank() && !materialKeys.add(materialKey)) {
                errors.add(error(row, "material_code", "OPENING_MATERIAL_DUPLICATED_IN_FILE",
                        "文件内同一仓库与辅料只能有一条期初记录", material));
            }
        }
        return new Validation(List.copyOf(errors));
    }

    @Override
    public Result commit(List<ImportRow> rows, AuthenticatedActor actor) {
        Validation validation = validate(rows);
        if (!validation.valid()) throw new IllegalArgumentException("Opening inventory must be valid before commit");
        int created = 0, unchanged = 0;
        for (ImportRow row : dataRows(rows)) {
            String warehouse = normalize(value(row, "warehouse_code"));
            String materialCode = normalize(value(row, "material_code"));
            String reference = value(row, "opening_reference");
            String asOfDate = value(row, "as_of_date");
            MaterialRef material = material(materialCode);
            InventoryOpeningService.OpeningResult result = openings.postOpening(
                    command(row, warehouse, material, reference, asOfDate), actor);
            if (result.replayed()) unchanged++; else created++;
        }
        return new Result(created, 0, unchanged);
    }

    private boolean enabledWarehouse(String code) {
        return jdbc.sql("SELECT COUNT(*) FROM warehouse WHERE id=:id AND UPPER(warehouse_code)=:code AND enabled=TRUE")
                .param("id", InventoryRepository.WAREHOUSE_ID).param("code", code).query(Integer.class).single() == 1;
    }

    private MaterialRef material(String code) {
        return jdbc.sql("""
                SELECT m.id,m.unit,b.opening_transaction_id,t.request_id,t.payload_hash FROM auxiliary_material m
                LEFT JOIN inventory_balance b ON b.warehouse_id=:warehouse AND b.auxiliary_material_id=m.id
                LEFT JOIN inventory_transaction t ON t.id=b.opening_transaction_id
                WHERE m.material_code_normalized=:code AND m.status='ACTIVE'
                """).param("warehouse", InventoryRepository.WAREHOUSE_ID).param("code", code)
                .query((rs, n) -> new MaterialRef(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5)))
                .optional().orElse(null);
    }

    private InventoryOpeningService.OpeningCommand command(ImportRow row, String warehouse, MaterialRef material,
            String reference, String asOfDate) {
        return new InventoryOpeningService.OpeningCommand(requestId(warehouse, material.id(), reference), material.id(),
                new BigDecimal(value(row, "opening_quantity")), material.unit(),
                new BigDecimal(value(row, "safety_stock")), "期初导入 " + asOfDate + " [" + reference + "]");
    }

    private boolean validReplay(ImportRow row, String warehouse, String reference, MaterialRef material) {
        try {
            String quantityRaw = value(row, "opening_quantity");
            String safetyRaw = value(row, "safety_stock");
            String asOfDate = value(row, "as_of_date");
            if (quantityRaw.isBlank() || safetyRaw.isBlank() || reference.isBlank() || asOfDate.isBlank()) return false;
            InventoryOpeningService.OpeningCommand command = command(row, warehouse, material, reference, asOfDate);
            return command.requestId().equals(material.openingRequestId())
                    && openingPayloadHash(command).equals(material.openingPayloadHash());
        } catch (RuntimeException exception) { return false; }
    }

    private String requestId(String warehouse, String materialId, String reference) {
        String canonical = warehouse + "\u001f" + materialId + "\u001f" + normalize(reference);
        return "OPEN:" + sha256(canonical);
    }

    private String openingPayloadHash(InventoryOpeningService.OpeningCommand command) {
        String canonical = String.join("\u001f", command.requestId(), command.materialId(), "OPENING",
                command.quantity().stripTrailingZeros().toPlainString(), command.unit(), command.reason(), "",
                command.safetyStock().stripTrailingZeros().toPlainString());
        return sha256(canonical);
    }

    private String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private List<ImportRow> dataRows(List<ImportRow> rows) {
        return rows.stream().filter(row -> "导入模板".equals(row.sheet())).toList();
    }

    private void requiredCode(List<Error> errors, ImportRow row, String field, String raw) {
        if (!CODE.matcher(raw).matches()) errors.add(error(row, field, "CODE_INVALID",
                "编码必须为 1..64 位字母、数字、下划线或连字符，且以字母或数字开头", raw));
    }

    private void required(List<Error> errors, ImportRow row, String field, String raw, int max) {
        if (raw.isBlank() || raw.length() > max || raw.chars().anyMatch(Character::isISOControl)) {
            errors.add(error(row, field, "FIELD_REQUIRED_OR_INVALID", "字段必填、长度不得超过 " + max + " 且不能含控制字符", raw));
        }
    }

    private void optional(List<Error> errors, ImportRow row, String field, String raw, int max) {
        if (raw.length() > max || raw.chars().anyMatch(Character::isISOControl)) {
            errors.add(error(row, field, "FIELD_INVALID", "字段长度不得超过 " + max + " 且不能含控制字符", raw));
        }
    }

    private void decimal(List<Error> errors, ImportRow row, String field, String raw) {
        try {
            BigDecimal value = new BigDecimal(raw);
            if (value.signum() < 0 || value.precision() > 18 || Math.max(0, value.scale()) > 4)
                throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            errors.add(error(row, field, "DECIMAL_INVALID", "数量必须不小于 0，且最多 18 位、4 位小数", raw));
        }
    }

    private void date(List<Error> errors, ImportRow row, String raw) {
        try {
            LocalDate value = LocalDate.parse(raw);
            if (value.isAfter(LocalDate.now())) throw new DateTimeParseException("future", raw, 0);
        } catch (DateTimeParseException exception) {
            errors.add(error(row, "as_of_date", "OPENING_DATE_INVALID", "期初日期必须为 ISO 日期且不得晚于今天", raw));
        }
    }

    private Error error(ImportRow row, String field, String code, String reason, String raw) {
        return new Error(row.sheet(), row.rowNumber(), field, code, reason, raw);
    }

    private String value(ImportRow row, String field) { return row.data().getOrDefault(field, "").trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private record MaterialRef(String id, String unit, String openingTransactionId, String openingRequestId,
                               String openingPayloadHash) {}
}
