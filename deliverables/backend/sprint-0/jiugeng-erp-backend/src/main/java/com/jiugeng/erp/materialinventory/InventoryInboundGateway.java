package com.jiugeng.erp.materialinventory;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;

/** Public transaction-aware boundary used by purchasing receipts. */
public interface InventoryInboundGateway {
    Result postInbound(Command command, AuthenticatedActor actor);

    record Command(String requestId, String materialId, BigDecimal quantity, String unit, String reason) {}
    record Result(String transactionId, BigDecimal balanceAfter, boolean replayed) {}
}
