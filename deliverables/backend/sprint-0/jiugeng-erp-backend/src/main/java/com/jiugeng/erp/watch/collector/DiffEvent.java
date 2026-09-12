package com.jiugeng.erp.watch.collector;

import java.time.Instant;

public record DiffEvent(
        String eventKey,
        String storeKey,
        String marketplaceId,
        String asin,
        String detectedInRunId,
        Instant firstDetectedAt
) {
}
