package com.jiugeng.erp.watch.collector;

import java.time.Instant;
import java.util.Objects;

public record SnapshotManifest(
        String runId,
        String storeKey,
        String marketplaceId,
        RunStatus status,
        int itemCount,
        int uniqueAsinCount,
        int pageCount,
        boolean pagesComplete,
        String evidenceRef,
        String contentSha256,
        Instant completedAt
) {
    public SnapshotManifest {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(storeKey, "storeKey");
        Objects.requireNonNull(marketplaceId, "marketplaceId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(evidenceRef, "evidenceRef");
        Objects.requireNonNull(contentSha256, "contentSha256");
        Objects.requireNonNull(completedAt, "completedAt");
        if (itemCount < 0 || uniqueAsinCount < 0 || pageCount < 0) {
            throw new IllegalArgumentException("Snapshot counts cannot be negative");
        }
    }

    public enum RunStatus { SUCCEEDED, FAILED, BLOCKED_POLICY, RATE_LIMITED }
}
