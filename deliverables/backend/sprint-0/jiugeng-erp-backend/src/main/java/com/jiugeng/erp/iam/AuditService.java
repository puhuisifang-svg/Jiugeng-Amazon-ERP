package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.AuditTypes.Actor;
import static com.jiugeng.erp.iam.AuditTypes.AuditEvent;
import static com.jiugeng.erp.iam.AuditTypes.AuditFilter;
import static com.jiugeng.erp.iam.AuditTypes.AuditPage;
import static com.jiugeng.erp.iam.AuditTypes.Outcome;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditService implements AuditTrail, AuditHistoryLookup {
    private final AuditRepository repository;
    private final ObjectMapper objectMapper;

    AuditService(AuditRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    void append(Actor actor, String category, String action, String resourceType, String resourceId,
            Outcome outcome, Object beforeValue, Object afterValue, String detail) {
        repository.append(new AuditEvent(
                UUID.randomUUID().toString(),
                category,
                action,
                actor.userId(),
                actor.loginName(),
                actor.displayName(),
                resourceType,
                resourceId,
                outcome,
                tree(beforeValue),
                tree(afterValue),
                detail,
                Instant.now()));
    }

    void security(Actor actor, String action, String resourceId, Outcome outcome, String detail) {
        append(actor, "SECURITY", action, "ACCOUNT", resourceId, outcome, null, null, detail);
    }

    void account(Actor actor, String action, IamTypes.UserView before, IamTypes.UserView after, String detail) {
        String resourceId = after != null ? after.id() : before.id();
        append(actor, "ACCOUNT", action, "ACCOUNT", resourceId, Outcome.SUCCESS,
                safeUser(before), safeUser(after), detail);
    }

    @Override
    public void record(BusinessOperation operation) {
        if (operation == null || operation.actorUserId() == null || operation.actorLoginName() == null
                || operation.actorLoginName().isBlank() || operation.action() == null
                || operation.resourceType() == null || !operation.action().matches("[A-Z][A-Z0-9_]{0,63}")
                || !operation.resourceType().matches("[A-Z][A-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("Business audit actor, action and resource type are required");
        }
        append(new Actor(operation.actorUserId(), operation.actorLoginName(), operation.actorDisplayName()),
                "BUSINESS", operation.action(), operation.resourceType(), operation.resourceId(),
                operation.success() ? Outcome.SUCCESS : Outcome.FAILURE,
                operation.beforeValue(), operation.afterValue(), operation.detail());
    }

    @Transactional(readOnly = true)
    AuditPage find(AuditFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new IamException(400, "AUDIT_TIME_RANGE_INVALID", "开始时间不能晚于结束时间");
        }
        return AuditPage.of(repository.find(filter), filter.page(), filter.size(), repository.count(filter));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<HistoryEvent> find(String resourceType, String resourceId) {
        if (resourceType == null || !resourceType.matches("[A-Z][A-Z0-9_]{0,63}")
                || resourceId == null || resourceId.isBlank() || resourceId.length() > 128) {
            throw new IllegalArgumentException("Valid audit resource type and id are required");
        }
        return repository.findResource(resourceType, resourceId).stream().map(event -> new HistoryEvent(
                event.id(), event.action(), event.actorLoginName(), event.actorDisplayName(), event.beforeValue(),
                event.afterValue(), event.detail(), event.occurredAt())).toList();
    }

    private JsonNode tree(Object value) {
        return value == null ? null : objectMapper.valueToTree(value);
    }

    private Map<String, Object> safeUser(IamTypes.UserView user) {
        if (user == null) return null;
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", user.id());
        snapshot.put("loginName", user.loginName());
        snapshot.put("displayName", user.displayName());
        snapshot.put("role", user.role());
        snapshot.put("status", user.status());
        snapshot.put("mustChangePassword", user.mustChangePassword());
        snapshot.put("lockedAt", user.lockedAt());
        return snapshot;
    }
}
