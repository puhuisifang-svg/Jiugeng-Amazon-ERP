package com.jiugeng.erp.watch.collector;

import java.time.Instant;
import java.util.Objects;

public record SourceApproval(
        Status status,
        SourceClass sourceClass,
        boolean requiresBypass,
        Instant reviewedAt
) {
    public SourceApproval {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sourceClass, "sourceClass");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
    }

    public enum Status { APPROVED, PENDING, REJECTED }

    public enum SourceClass { APPROVED_API, PUBLIC_EXPORT, MANUAL_IMPORT }
}
