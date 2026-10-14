package com.jiugeng.erp.materialinventory;

import java.math.BigDecimal;
import java.time.Instant;

final class InventoryTypes {
    private InventoryTypes() {}
    enum TransactionType { OPENING, INBOUND, OUTBOUND, ADJUST_IN, ADJUST_OUT, REVERSAL }
    record TransactionCommand(String requestId, String materialId, TransactionType type, BigDecimal quantity,
            String unit, String reason, String referenceTransactionId) {}
    record BalanceView(String warehouseCode, String materialId, String materialCode, String materialName,
            BigDecimal quantity, BigDecimal safetyStock, String unit, boolean lowStock, Instant updatedAt) {}
    record TransactionView(String id, String requestId, String warehouseCode, String materialId, String materialCode,
            String materialName, TransactionType type, BigDecimal quantity, BigDecimal signedQuantity, String unit,
            String reason, String referenceTransactionId, BigDecimal balanceAfter, String actorLoginName,
            Instant occurredAt) {}
    record TransactionResult(TransactionView item, BalanceView balance, boolean replayed) {}
}
