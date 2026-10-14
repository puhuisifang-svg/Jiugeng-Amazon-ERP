package com.jiugeng.erp.iam;

import java.util.Map;

/**
 * Public IAM module boundary for appending a business operation to the audit trail.
 * Callers must pass only approved, secret-free snapshots and invoke this within the
 * same transaction as the business change.
 */
public interface AuditTrail {
    void record(BusinessOperation operation);

    record BusinessOperation(
            String actorUserId,
            String actorLoginName,
            String actorDisplayName,
            String action,
            String resourceType,
            String resourceId,
            boolean success,
            Map<String, ?> beforeValue,
            Map<String, ?> afterValue,
            String detail) {
    }
}
