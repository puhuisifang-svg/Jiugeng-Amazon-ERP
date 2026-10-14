package com.jiugeng.erp.selection;

import static com.jiugeng.erp.selection.SelectionTypes.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SelectionRepository {
    private final JdbcClient jdbc;
    SelectionRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    List<CandidateView> findAll(String keyword, CandidateStatus status, String owner) {
        StringBuilder sql = new StringBuilder("""
                SELECT * FROM selection_candidate WHERE 1=1
                """);
        if (keyword != null) sql.append(" AND (UPPER(asin) LIKE :keyword OR UPPER(title) LIKE :keyword OR UPPER(COALESCE(category,'')) LIKE :keyword OR UPPER(COALESCE(owner,'')) LIKE :keyword OR EXISTS (SELECT 1 FROM selection_candidate_tag t WHERE t.candidate_id=selection_candidate.id AND UPPER(t.tag) LIKE :keyword))");
        if (status != null) sql.append(" AND status=:status");
        if (owner != null) sql.append(" AND UPPER(owner)=:owner");
        sql.append(" ORDER BY discovered_at DESC, id");
        var query = jdbc.sql(sql.toString());
        if (keyword != null) query = query.param("keyword", "%" + keyword.trim().toUpperCase(java.util.Locale.ROOT) + "%");
        if (status != null) query = query.param("status", status.name());
        if (owner != null) query = query.param("owner", owner.trim().toUpperCase(java.util.Locale.ROOT));
        return query.query(this::mapCandidate).list();
    }

    Optional<CandidateView> findById(String id) {
        return jdbc.sql("SELECT * FROM selection_candidate WHERE id=:id").param("id", id)
                .query(this::mapCandidate).optional();
    }

    boolean asinExists(String normalized) {
        return jdbc.sql("SELECT COUNT(*) FROM selection_candidate WHERE asin_normalized=:asin")
                .param("asin", normalized).query(Integer.class).single() > 0;
    }

    void insert(String id, String asin, String title, int score, String url, String actorId) {
        jdbc.sql("""
                INSERT INTO selection_candidate (id, asin, asin_normalized, title, status, score, source_url,
                    source, discovered_at, created_by, updated_by)
                VALUES (:id,:asin,:asin,:title,'NEW',:score,:url,'MANUAL',CURRENT_TIMESTAMP,:actor,:actor)
                """).param("id", id).param("asin", asin).param("title", title).param("score", score)
                .param("url", url).param("actor", actorId).update();
    }

    void updateEvaluation(String id, EvaluationInput input, String actorId) {
        jdbc.sql("""
                UPDATE selection_candidate SET category=:category, price=:price, rating=:rating,
                    review_count=:reviews, owner=:owner, selection_reason=:reason,
                    updated_by=:actor, updated_at=CURRENT_TIMESTAMP WHERE id=:id
                """).param("category", input.category()).param("price", input.price()).param("rating", input.rating())
                .param("reviews", input.reviewCount()).param("owner", input.owner()).param("reason", input.selectionReason())
                .param("actor", actorId).param("id", id).update();
        jdbc.sql("DELETE FROM selection_candidate_tag WHERE candidate_id=:id").param("id", id).update();
        int order = 0;
        for (String tag : input.tags()) {
            jdbc.sql("INSERT INTO selection_candidate_tag (candidate_id,tag,tag_normalized,display_order) VALUES (:id,:tag,:normalized,:sort)")
                    .param("id", id).param("tag", tag).param("normalized", normalize(tag)).param("sort", order++).update();
        }
    }

    void transition(String id, CandidateStatus status, String reason, String actorId) {
        jdbc.sql("UPDATE selection_candidate SET status=:status,rejection_reason=:reason,updated_by=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("status", status.name()).param("reason", reason).param("actor", actorId).param("id", id).update();
    }

    void insertNote(String id, String candidateId, String content, String authorId, String authorName, String role) {
        jdbc.sql("""
                INSERT INTO selection_candidate_note (id,candidate_id,content,author_id,author_name,author_role)
                VALUES (:id,:candidate,:content,:author,:name,:role)
                """).param("id", id).param("candidate", candidateId).param("content", content)
                .param("author", authorId).param("name", authorName).param("role", role).update();
    }

    void insertEvidence(String id, String candidateId, String name, String url, String description,
            String actorId, String actorName, String role) {
        jdbc.sql("""
                INSERT INTO selection_candidate_evidence
                    (id,candidate_id,name,url,description,added_by_id,added_by_name,added_by_role)
                VALUES (:id,:candidate,:name,:url,:description,:actor,:actorName,:role)
                """).param("id", id).param("candidate", candidateId).param("name", name).param("url", url)
                .param("description", description).param("actor", actorId).param("actorName", actorName)
                .param("role", role).update();
    }

    List<NoteView> notes(String candidateId) {
        return jdbc.sql("SELECT * FROM selection_candidate_note WHERE candidate_id=:id ORDER BY created_at,id")
                .param("id", candidateId).query((rs,n) -> new NoteView(rs.getString("id"), rs.getString("content"),
                        rs.getString("author_id"), rs.getString("author_name"), rs.getString("author_role"), instant(rs,"created_at"))).list();
    }

    List<EvidenceView> evidence(String candidateId) {
        return jdbc.sql("SELECT * FROM selection_candidate_evidence WHERE candidate_id=:id ORDER BY created_at,id")
                .param("id", candidateId).query((rs,n) -> new EvidenceView(rs.getString("id"), rs.getString("name"),
                        rs.getString("url"), rs.getString("description"), rs.getString("added_by_id"),
                        rs.getString("added_by_name"), rs.getString("added_by_role"), instant(rs,"created_at"))).list();
    }

    private CandidateView mapCandidate(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("id");
        List<String> tags = jdbc.sql("SELECT tag FROM selection_candidate_tag WHERE candidate_id=:id ORDER BY display_order,tag_normalized")
                .param("id", id).query(String.class).list();
        return new CandidateView(id, rs.getString("asin"), rs.getString("title"), CandidateStatus.valueOf(rs.getString("status")),
                rs.getInt("score"), rs.getString("rejection_reason"), rs.getString("source_url"), rs.getString("category"),
                rs.getBigDecimal("price"), rs.getBigDecimal("rating"), integer(rs,"review_count"), rs.getString("owner"),
                tags, rs.getString("selection_reason"), CandidateSource.valueOf(rs.getString("source")),
                rs.getString("source_store"), instant(rs,"discovered_at"), rs.getString("source_run_id"), rs.getString("source_event_id"));
    }

    private Integer integer(ResultSet rs, String column) throws SQLException { int value=rs.getInt(column); return rs.wasNull()?null:value; }
    private Instant instant(ResultSet rs, String column) throws SQLException { return rs.getTimestamp(column).toInstant(); }
    static String normalize(String value) { return value.trim().toUpperCase(java.util.Locale.ROOT); }
}
