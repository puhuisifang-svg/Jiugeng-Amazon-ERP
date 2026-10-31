package com.jiugeng.erp.iam;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** Public read boundary for a domain module to expose the complete history of one resource. */
public interface AuditHistoryLookup {
    List<HistoryEvent> find(String resourceType, String resourceId);

    record HistoryPage(List<HistoryEvent> items,long total,int page,int size) {}
    HistoryPage page(String resourceType,String resourceId,int page,int size);

    record HistoryEvent(String id, String action, String actorLoginName, String actorDisplayName,
                        JsonNode beforeValue, JsonNode afterValue, String detail, Instant occurredAt) {}
}
