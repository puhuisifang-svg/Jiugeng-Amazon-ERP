package com.jiugeng.erp.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

final class MonitoringTypes {
    private MonitoringTypes() {}
    record Target(String id, String marketplace, String asin, boolean enabled, String ownerId, String ownerName,
                  List<String> tags, String remark, long version, JsonNode fields, String source,
                  Instant observedAt, String lastStatus, String lastReason, Instant lastAttemptAt,
                  Instant createdAt, boolean followed) {}
    record Page<T>(List<T> items, long total, long activeCount, int limit) {}
    record CreateInput(String requestId, List<String> asins, List<String> tags, String remark) {}
    record RowResult(String asin, String id, String status, String message) {}
    record VersionedId(String id, long version) {}
    record BatchInput(List<VersionedId> targets, String action, List<String> tags) {}
    record SnapshotInput(String requestId, long version, Instant observedAt, String source,
                         String quality, JsonNode fields, String reason) {}
    record Snapshot(String id, String targetId, String requestId, Instant observedAt, Instant recordedAt,
                    String source, String quality, String decision, String reason, JsonNode fields, JsonNode changes) {}
    record Detail(Target target, List<Snapshot> snapshots) {}
    record Preferences(List<String> followed, List<String> columns) {}
}
