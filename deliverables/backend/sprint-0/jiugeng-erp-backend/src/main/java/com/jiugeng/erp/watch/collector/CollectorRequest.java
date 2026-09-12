package com.jiugeng.erp.watch.collector;

import java.util.Objects;

public record CollectorRequest(
        String runId,
        String storeKey,
        String marketplaceId,
        SourceApproval approval
) {
    public CollectorRequest {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(storeKey, "storeKey");
        Objects.requireNonNull(marketplaceId, "marketplaceId");
        Objects.requireNonNull(approval, "approval");
    }
}
