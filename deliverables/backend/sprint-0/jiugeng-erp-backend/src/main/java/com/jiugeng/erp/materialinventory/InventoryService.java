package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.InventoryTypes.BalanceView;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionCommand;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionResult;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionType;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionView;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryService implements InventoryOpeningService, InventoryInboundGateway {
    private final InventoryRepository repository;
    private final AuditTrail auditTrail;

    InventoryService(InventoryRepository repository, AuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    List<BalanceView> balances() { return repository.balances(); }

    @Transactional(readOnly = true)
    List<TransactionView> transactions(String materialId) { return repository.transactions(materialId); }

    @Transactional
    TransactionResult transact(TransactionCommand command, AuthenticatedActor actor) {
        if (command.type() == null || command.type() == TransactionType.OPENING) {
            throw new MaterialException(400, "INVENTORY_TYPE_NOT_ALLOWED", "普通库存命令不允许提交期初类型");
        }
        return post(command, actor, false, null);
    }

    @Override
    @Transactional
    public OpeningResult postOpening(OpeningCommand command, AuthenticatedActor actor) {
        if (command.quantity() == null || command.quantity().signum() < 0) {
            throw new MaterialException(400, "OPENING_QUANTITY_INVALID", "期初数量不能小于 0");
        }
        BigDecimal safety = nonNegative(command.safetyStock(), "SAFETY_STOCK_INVALID", "安全库存不能小于 0");
        TransactionResult result = post(new TransactionCommand(command.requestId(), command.materialId(),
                TransactionType.OPENING, command.quantity(), command.unit(), command.reason(), null), actor, true, safety);
        return new OpeningResult(result.item().id(), result.balance().quantity(), result.replayed());
    }

    @Override
    @Transactional
    public InventoryInboundGateway.Result postInbound(InventoryInboundGateway.Command command, AuthenticatedActor actor) {
        TransactionResult result = post(new TransactionCommand(command.requestId(), command.materialId(),
                TransactionType.INBOUND, command.quantity(), command.unit(), command.reason(), null), actor, false, null);
        return new InventoryInboundGateway.Result(result.item().id(), result.balance().quantity(), result.replayed());
    }

    @Transactional
    BalanceView updateSafetyStock(String materialId, BigDecimal safetyStock, AuthenticatedActor actor) {
        BigDecimal valid = nonNegative(safetyStock, "SAFETY_STOCK_INVALID", "安全库存不能小于 0");
        repository.lockWarehouse();
        InventoryRepository.MaterialRef material = repository.activeMaterial(required(materialId, "辅料", 36));
        repository.ensureBalance(material);
        BalanceView before = repository.balance(material.id()).orElseThrow();
        repository.updateSafetyStock(material.id(), valid);
        BalanceView after = repository.balance(material.id()).orElseThrow();
        auditTrail.record(operation(actor, "INVENTORY_SAFETY_STOCK_UPDATE", material.id(), snapshot(before),
                snapshot(after), "更新固定仓安全库存"));
        return after;
    }

    private TransactionResult post(TransactionCommand command, AuthenticatedActor actor, boolean opening,
            BigDecimal openingSafety) {
        String requestId = required(command.requestId(), "请求号", 100);
        String materialId = required(command.materialId(), "辅料", 36);
        String reason = required(command.reason(), "原因", 500);
        String unit = required(command.unit(), "单位", 32);
        String reference = optional(command.referenceTransactionId(), 36);
        BigDecimal suppliedQuantity = command.quantity();
        String hash = hash(requestId, materialId, command.type(), suppliedQuantity, unit, reason, reference,
                opening ? openingSafety : null);

        repository.lockWarehouse();
        TransactionView existing = repository.transactionByRequest(requestId).orElse(null);
        if (existing != null) {
            if (!hash.equals(repository.payloadHash(requestId))) {
                throw new MaterialException(409, "INVENTORY_REQUEST_CONFLICT", "同一 requestId 的载荷不一致");
            }
            return result(existing, true);
        }

        InventoryRepository.MaterialRef material = repository.activeMaterial(materialId);
        if (!material.unit().equalsIgnoreCase(unit)) {
            throw new MaterialException(409, "INVENTORY_UNIT_MISMATCH", "库存单位必须与辅料主档一致");
        }
        repository.ensureBalance(material);
        InventoryRepository.BalanceState balance = repository.lockBalance(materialId);
        if (opening && balance.openingTransactionId() != null) {
            throw new MaterialException(409, "OPENING_ALREADY_EXISTS", "该辅料已存在期初流水");
        }

        BigDecimal quantity;
        BigDecimal delta;
        String referenceId = null;
        if (command.type() == TransactionType.REVERSAL) {
            if (reference == null) throw new MaterialException(400, "REVERSAL_REFERENCE_REQUIRED", "冲销必须引用原流水");
            TransactionView original = repository.transactionById(reference)
                    .orElseThrow(() -> new MaterialException(404, "INVENTORY_TRANSACTION_NOT_FOUND", "原流水不存在"));
            if (!original.materialId().equals(materialId) || original.type() == TransactionType.REVERSAL) {
                throw new MaterialException(409, "REVERSAL_REFERENCE_INVALID", "只能冲销同一辅料的非冲销流水");
            }
            if (repository.isReversed(reference)) throw new MaterialException(409, "REVERSAL_ALREADY_EXISTS", "原流水已被冲销");
            quantity = original.quantity(); delta = original.signedQuantity().negate(); referenceId = reference;
        } else {
            if (reference != null) throw new MaterialException(400, "INVENTORY_REFERENCE_INVALID", "非冲销流水不得引用原流水");
            quantity = suppliedQuantity;
            if ((!opening && (quantity == null || quantity.signum() <= 0))
                    || (opening && (quantity == null || quantity.signum() < 0))) {
                throw new MaterialException(400, "INVENTORY_QUANTITY_INVALID", "库存数量不符合流水类型要求");
            }
            delta = switch (command.type()) {
                case OPENING, INBOUND, ADJUST_IN -> quantity;
                case OUTBOUND, ADJUST_OUT -> quantity.negate();
                case REVERSAL -> throw new IllegalStateException();
            };
        }
        BigDecimal after = balance.quantity().add(delta);
        if (after.signum() < 0) throw new MaterialException(409, "NEGATIVE_INVENTORY", "库存不足，禁止产生负库存");
        String id = UUID.randomUUID().toString();
        repository.updateBalance(materialId, after, unit);
        repository.insertTransaction(id, requestId, hash, materialId, command.type(), quantity, delta, unit, reason,
                referenceId, after, actor.userId(), actor.loginName());
        if (opening) repository.markOpening(materialId, id, openingSafety);
        TransactionView created = repository.transactionById(id).orElseThrow();
        auditTrail.record(operation(actor, "INVENTORY_" + command.type().name(), id, null, snapshot(created),
                "固定仓不可变库存流水"));
        return result(created, false);
    }

    private TransactionResult result(TransactionView transaction, boolean replayed) {
        BalanceView current = repository.balance(transaction.materialId()).orElseThrow();
        BalanceView atResult = new BalanceView(current.warehouseCode(), current.materialId(), current.materialCode(),
                current.materialName(), transaction.balanceAfter(), current.safetyStock(), transaction.unit(),
                transaction.balanceAfter().compareTo(current.safetyStock()) < 0, current.updatedAt());
        return new TransactionResult(transaction, atResult, replayed);
    }

    private BigDecimal nonNegative(BigDecimal value, String code, String message) {
        if (value == null || value.signum() < 0) throw new MaterialException(400, code, message);
        return value;
    }

    private String required(String value, String label, int max) {
        String result = optional(value, max);
        if (result == null) throw new MaterialException(400, "INVENTORY_FIELD_INVALID", label + "不能为空");
        return result;
    }

    private String optional(String value, int max) {
        if (value == null) return null;
        String result = value.trim();
        if (result.isEmpty()) return null;
        if (result.length() > max) throw new MaterialException(400, "INVENTORY_FIELD_INVALID", "字段长度超限");
        return result;
    }

    private String hash(String requestId, String materialId, TransactionType type, BigDecimal quantity, String unit,
            String reason, String reference, BigDecimal openingSafety) {
        String canonical = String.join("\u001f", requestId, materialId, type == null ? "" : type.name(),
                quantity == null ? "" : quantity.stripTrailingZeros().toPlainString(), unit, reason,
                reference == null ? "" : reference);
        if (openingSafety != null) canonical += "\u001f" + openingSafety.stripTrailingZeros().toPlainString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor, String action, String id,
            Map<String, ?> before, Map<String, ?> after, String detail) {
        return new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(), action,
                "INVENTORY", id, true, before, after, detail);
    }

    private Map<String, Object> snapshot(BalanceView balance) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("materialId", balance.materialId());
        value.put("quantity", balance.quantity()); value.put("safetyStock", balance.safetyStock());
        value.put("unit", balance.unit()); value.put("lowStock", balance.lowStock()); return value;
    }

    private Map<String, Object> snapshot(TransactionView transaction) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("requestId", transaction.requestId());
        value.put("materialId", transaction.materialId()); value.put("type", transaction.type());
        value.put("quantity", transaction.quantity()); value.put("signedQuantity", transaction.signedQuantity());
        value.put("balanceAfter", transaction.balanceAfter()); value.put("referenceTransactionId", transaction.referenceTransactionId());
        return value;
    }
}
