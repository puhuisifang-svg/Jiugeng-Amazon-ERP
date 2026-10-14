package com.jiugeng.erp.iam;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

final class AuditTypes {
    private AuditTypes() {
    }

    enum Outcome {
        SUCCESS, FAILURE
    }

    record Actor(String userId, String loginName, String displayName) {
        static Actor authenticated(IamTypes.SessionPrincipal principal) {
            return new Actor(principal.userId(), principal.loginName(), principal.displayName());
        }

        static Actor attemptedLogin(String userId, String loginName, String displayName) {
            return new Actor(userId, loginName, displayName);
        }

        static Actor system() {
            return new Actor(null, "system", "系统");
        }
    }

    record AuditEvent(String id, String category, String action, String actorUserId, String actorLoginName,
                      String actorDisplayName, String resourceType, String resourceId, Outcome outcome,
                      JsonNode beforeValue, JsonNode afterValue, String detail, Instant occurredAt) {
    }

    record AuditFilter(String actorUserId, String actorLoginName, String category, String action,
                       String resourceType, Outcome outcome, Instant from, Instant to, int page, int size) {
    }

    record AuditPage(List<AuditEvent> items, int page, int size, long totalElements, int totalPages) {
        static AuditPage of(List<AuditEvent> items, int page, int size, long totalElements) {
            int pages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
            return new AuditPage(items, page, size, totalElements, pages);
        }
    }
}
