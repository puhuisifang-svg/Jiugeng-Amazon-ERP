package com.jiugeng.erp.materialinventory;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;

/** Stable boundary reserved for the validated ERP-017 opening-inventory import. */
public interface InventoryOpeningService {
    OpeningResult postOpening(OpeningCommand command, AuthenticatedActor actor);

    record OpeningCommand(String requestId, String materialId, BigDecimal quantity, String unit,
            BigDecimal safetyStock, String reason) {}
    record OpeningResult(String transactionId, BigDecimal balance, boolean replayed) {}
}
