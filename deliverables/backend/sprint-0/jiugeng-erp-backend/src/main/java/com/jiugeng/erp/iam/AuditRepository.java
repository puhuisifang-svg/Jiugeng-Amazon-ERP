package com.jiugeng.erp.iam;

import static com.jiugeng.erp.iam.AuditTypes.AuditEvent;
import static com.jiugeng.erp.iam.AuditTypes.AuditFilter;
import static com.jiugeng.erp.iam.AuditTypes.Outcome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AuditRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    AuditRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    void append(AuditEvent event) {
        jdbc.sql("""
                INSERT INTO audit_event (
                    id, category, action, actor_user_id, actor_login_name, actor_display_name,
                    resource_type, resource_id, outcome, before_value, after_value, detail, occurred_at
                ) VALUES (
                    :id, :category, :action, :actorUserId, :actorLoginName, :actorDisplayName,
                    :resourceType, :resourceId, :outcome, :beforeValue, :afterValue, :detail, :occurredAt
                )
                """)
                .param("id", event.id())
                .param("category", event.category())
                .param("action", event.action())
                .param("actorUserId", event.actorUserId())
                .param("actorLoginName", event.actorLoginName())
                .param("actorDisplayName", event.actorDisplayName())
                .param("resourceType", event.resourceType())
                .param("resourceId", event.resourceId())
                .param("outcome", event.outcome().name())
                .param("beforeValue", json(event.beforeValue()))
                .param("afterValue", json(event.afterValue()))
                .param("detail", event.detail())
                .param("occurredAt", event.occurredAt())
                .update();
    }

    List<AuditEvent> find(AuditFilter filter) {
        Query query = query(filter);
        query.sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset");
        query.parameters.put("limit", filter.size());
        query.parameters.put("offset", filter.page() * filter.size());
        return jdbc.sql(query.sql.toString()).params(query.parameters).query(this::mapEvent).list();
    }

    long count(AuditFilter filter) {
        Query query = query(filter);
        return jdbc.sql("SELECT COUNT(*)" + query.sql.substring("SELECT *".length()))
                .params(query.parameters).query(Long.class).single();
    }

    List<AuditEvent> findResource(String resourceType, String resourceId) {
        return jdbc.sql("SELECT * FROM audit_event WHERE resource_type=:type AND resource_id=:id"
                        + " ORDER BY occurred_at DESC,id DESC LIMIT 200")
                .param("type", resourceType).param("id", resourceId).query(this::mapEvent).list();
    }

    List<AuditEvent> findResourcePage(String type,String id,int page,int size) {
        return jdbc.sql("SELECT * FROM audit_event WHERE resource_type=:type AND resource_id=:id ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset")
            .param("type",type).param("id",id).param("size",size).param("offset",(long)(page-1)*size).query(this::mapEvent).list();
    }
    long countResource(String type,String id) {
        return jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE resource_type=:type AND resource_id=:id").param("type",type).param("id",id).query(Long.class).single();
    }

    AuditTypes.FilterOptions options(String userId) {
        String scope = userId == null ? "" : " WHERE actor_user_id = :userId";
        Map<String, Object> params = userId == null ? Map.of() : Map.of("userId", userId);
        var actors = jdbc.sql("SELECT actor_login_name, MAX(actor_display_name) AS display_name FROM audit_event"
                + scope + " GROUP BY actor_login_name ORDER BY actor_login_name")
                .params(params).query((rs, n) -> new AuditTypes.FilterOption(rs.getString("actor_login_name"),
                        rs.getString("display_name"))).list();
        return new AuditTypes.FilterOptions(actors, distinct("category", scope, params),
                distinct("action", scope, params), distinct("resource_type", scope, params));
    }

    private List<String> distinct(String column, String scope, Map<String, Object> params) {
        return jdbc.sql("SELECT DISTINCT " + column + " FROM audit_event" + scope + " ORDER BY " + column)
                .params(params).query(String.class).list();
    }

    private void add(StringBuilder sql, Map<String, Object> parameters, String column, String parameter, List<String> values) {
        if (values == null || values.isEmpty()) return;
        var selected = values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
        if (selected.isEmpty()) return;
        sql.append(" AND ").append(column).append(" IN (:").append(parameter).append(")");
        parameters.put(parameter, selected);
    }

    private Query query(AuditFilter filter) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_event WHERE 1 = 1");
        Map<String, Object> parameters = new HashMap<>();
        add(sql, parameters, "actor_user_id", "actorUserId", filter.actorUserId());
        add(sql, parameters, "actor_login_name", "actorLoginName", filter.actorLoginName());
        add(sql, parameters, "category", "category", filter.category());
        add(sql, parameters, "action", "action", filter.action());
        add(sql, parameters, "resource_type", "resourceType", filter.resourceType());
        if (filter.outcome() != null) add(sql, parameters, "outcome", "outcome", filter.outcome().name());
        if (filter.from() != null) {
            sql.append(" AND occurred_at >= :fromTime");
            parameters.put("fromTime", filter.from());
        }
        if (filter.to() != null) {
            sql.append(" AND occurred_at <= :toTime");
            parameters.put("toTime", filter.to());
        }
        return new Query(sql, parameters);
    }

    private void add(StringBuilder sql, Map<String, Object> parameters, String column, String parameter, String value) {
        if (value == null || value.isBlank()) return;
        sql.append(" AND ").append(column).append(" = :").append(parameter);
        parameters.put(parameter, value);
    }

    private AuditEvent mapEvent(ResultSet rs, int rowNumber) throws SQLException {
        return new AuditEvent(
                rs.getString("id"),
                rs.getString("category"),
                rs.getString("action"),
                rs.getString("actor_user_id"),
                rs.getString("actor_login_name"),
                rs.getString("actor_display_name"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                Outcome.valueOf(rs.getString("outcome")),
                parseJson(rs.getString("before_value")),
                parseJson(rs.getString("after_value")),
                rs.getString("detail"),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private String json(JsonNode value) {
        return value == null ? null : value.toString();
    }

    private JsonNode parseJson(String value) throws SQLException {
        if (value == null) return null;
        try {
            return objectMapper.readTree(value);
        }
        catch (JsonProcessingException exception) {
            throw new SQLException("Stored audit snapshot is not valid JSON", exception);
        }
    }

    private record Query(StringBuilder sql, Map<String, Object> parameters) {
    }
}
