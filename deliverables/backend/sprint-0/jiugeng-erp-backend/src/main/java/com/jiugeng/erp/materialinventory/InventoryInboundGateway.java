package com.jiugeng.erp.materialinventory;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;

/** Public transaction-aware boundary used by purchasing receipts. */
public interface InventoryInboundGateway {
    final class Failure extends RuntimeException {
        public final int status; public final String code;
        public Failure(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
    Result postInbound(Command command, AuthenticatedActor actor);

    record Command(String requestId, String materialId, BigDecimal quantity, String unit, String reason) {}
    record Result(String transactionId, BigDecimal balanceAfter, boolean replayed) {}
}
