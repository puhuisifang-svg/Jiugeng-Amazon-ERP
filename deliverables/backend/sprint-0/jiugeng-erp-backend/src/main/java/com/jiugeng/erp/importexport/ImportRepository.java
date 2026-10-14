package com.jiugeng.erp.importexport;

import static com.jiugeng.erp.importexport.ImportTypes.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ImportRepository {
    private final JdbcClient jdbc; private final ObjectMapper mapper;
    ImportRepository(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    Optional<BatchRow> uploadByKey(String actorId, String key) {
        return jdbc.sql(select() + " WHERE actor_user_id=:actor AND upload_idempotency_key=:key")
                .param("actor", actorId).param("key", key).query(this::map).optional();
    }
    BatchRow batch(String id) { return optional(id).orElseThrow(() -> new ImportException(404, "IMPORT_BATCH_NOT_FOUND", "导入批次不存在")); }
    Optional<BatchRow> optional(String id) { return jdbc.sql(select() + " WHERE id=:id").param("id", id).query(this::map).optional(); }
    List<BatchView> list() { return jdbc.sql(select() + " ORDER BY created_at DESC, id DESC").query((rs, row) -> map(rs, row).view()).list(); }

    Set<String> activeStorageKeys() {
        return Set.copyOf(jdbc.sql("""
                SELECT storage_key FROM import_batch
                WHERE status IN ('QUEUED','VALIDATING','VALIDATED','COMMITTING') AND storage_key IS NOT NULL
                UNION
                SELECT result_storage_key FROM import_batch
                WHERE status IN ('QUEUED','VALIDATING','VALIDATED','COMMITTING') AND result_storage_key IS NOT NULL
                """).query(String.class).list());
    }

    void insert(String id, ImportType type, String version, String storageKey, String fileHash, String idemKey,
            String payloadHash, String actorId, String actorLogin, String source) {
        jdbc.sql("""
                INSERT INTO import_batch (id, import_type, template_version, status, phase, storage_key, file_hash,
                    upload_idempotency_key, upload_payload_hash, actor_user_id, actor_login_name,
                    revalidation_of_batch_id)
                VALUES (:id,:type,:version,'QUEUED','WAITING',:storage,:fileHash,:idem,:payload,:actor,:login,:source)
                """).param("id", id).param("type", type.name()).param("version", version).param("storage", storageKey)
                .param("fileHash", fileHash).param("idem", idemKey).param("payload", payloadHash)
                .param("actor", actorId).param("login", actorLogin).param("source", source).update();
    }
    void validating(String id) { updateState(id, Status.VALIDATING, Phase.PARSING); }
    void validated(String id, ValidationResult result, String previewVersion, Instant expiresAt) {
        jdbc.sql("""
                UPDATE import_batch SET status='VALIDATED', phase='WAITING_CONFIRMATION', total_rows=:total,
                    processed_rows=:total, error_count=:errors, warning_count=:warnings,
                    commit_allowed=:allowed, preview_version=:preview, preview_expires_at=:expires,
                    updated_at=CURRENT_TIMESTAMP WHERE id=:id AND status='VALIDATING'
                """).param("total", result.totalRows())
                .param("errors", result.issues().stream().filter(IssueInput::blocking).count())
                .param("warnings", result.issues().stream().filter(value -> value.severity().equals("WARNING")).count())
                .param("allowed", result.issues().stream().noneMatch(IssueInput::blocking))
                .param("preview", previewVersion).param("expires", expiresAt).param("id", id).update();
        int order = 0;
        for (IssueInput issue : result.issues()) insertIssue(id, issue, order++);
        order = 0; for (PreviewInput row : result.rows()) insertPreview(id, row, order++);
    }
    void cancel(String id, String resultStorageKey) {
        int changed = jdbc.sql("""
                UPDATE import_batch SET status='CANCELLED', phase='DONE', commit_allowed=FALSE,
                    result_storage_key=:resultStorageKey,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status IN ('QUEUED','VALIDATING','VALIDATED')
                """).param("resultStorageKey",resultStorageKey).param("id", id).update();
        if (changed == 0 && batch(id).status() != Status.CANCELLED)
            throw new ImportException(409, "IMPORT_NOT_CANCELLABLE", "当前批次状态不可取消");
    }
    boolean startCommit(String id) {
        return jdbc.sql("""
                UPDATE import_batch SET status='COMMITTING',phase='COMMITTING_ROWS',updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status='VALIDATED'
                """).param("id",id).update()==1;
    }
    void succeed(String id, ProductImportResult result, String resultStorageKey) {
        int changed=jdbc.sql("""
                UPDATE import_batch SET status='SUCCEEDED',phase='DONE',processed_rows=total_rows,
                    result_created_rows=:created,result_updated_rows=:updated,result_unchanged_rows=:unchanged,
                    result_storage_key=:resultStorageKey,committed_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND status='COMMITTING'
                """).param("created",result.created()).param("updated",result.updated()).param("unchanged",result.unchanged())
                .param("resultStorageKey",resultStorageKey).param("id",id).update();
        if(changed!=1)throw new ImportException(409,"IMPORT_STATE_CONFLICT","批次提交状态已变化");
    }
    void fail(String id,String code,String resultStorageKey){jdbc.sql("""
            UPDATE import_batch SET status='FAILED',phase='DONE',failure_code=:code,commit_allowed=FALSE,
                result_storage_key=:resultStorageKey,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND status='COMMITTING'
            """).param("code",code).param("resultStorageKey",resultStorageKey).param("id",id).update();}
    List<IssueView> issues(String id) {
        batch(id);
        return jdbc.sql("""
                SELECT id,severity,blocking,issue_scope,sheet_name,source_row_number,field_name,issue_code,reason,raw_value,suggestion
                FROM import_issue WHERE batch_id=:id ORDER BY sort_order
                """).param("id", id).query((rs, row) -> new IssueView(rs.getString("id"), rs.getString("severity"),
                    rs.getBoolean("blocking"), rs.getString("issue_scope"), rs.getString("sheet_name"),
                    (Integer) rs.getObject("source_row_number"), rs.getString("field_name"), rs.getString("issue_code"),
                    rs.getString("reason"), rs.getString("raw_value"), rs.getString("suggestion"))).list();
    }
    List<PreviewRowView> previewRows(String id) {
        return jdbc.sql("SELECT id,sheet_name,source_row_number,business_key,row_action,row_data_json FROM import_preview_row WHERE batch_id=:id ORDER BY sort_order")
                .param("id", id).query((rs, row) -> new PreviewRowView(rs.getString("id"), rs.getString("sheet_name"),
                        rs.getInt("source_row_number"), rs.getString("business_key"), rs.getString("row_action"),
                        readMap(rs.getString("row_data_json")))).list();
    }
    Optional<OperationRow> operation(String actor, String operation, String objectId, String key) {
        return jdbc.sql("SELECT payload_hash,response_batch_id FROM import_operation_idempotency WHERE actor_user_id=:actor AND operation_name=:operation AND object_id=:object AND idempotency_key=:key")
                .param("actor", actor).param("operation", operation).param("object", objectId).param("key", key)
                .query((rs, row) -> new OperationRow(rs.getString(1), rs.getString(2))).optional();
    }
    void operation(String actor, String operation, String objectId, String key, String payload, String response) {
        jdbc.sql("INSERT INTO import_operation_idempotency (id,actor_user_id,operation_name,object_id,idempotency_key,payload_hash,response_batch_id) VALUES (:id,:actor,:operation,:object,:key,:payload,:response)")
                .param("id", UUID.randomUUID().toString()).param("actor", actor).param("operation", operation)
                .param("object", objectId).param("key", key).param("payload", payload).param("response", response).update();
    }
    private void updateState(String id, Status status, Phase phase) {
        jdbc.sql("UPDATE import_batch SET status=:status,phase=:phase,updated_at=CURRENT_TIMESTAMP WHERE id=:id")
                .param("status", status.name()).param("phase", phase.name()).param("id", id).update();
    }
    private void insertIssue(String batch, IssueInput issue, int order) {
        jdbc.sql("""
                INSERT INTO import_issue (id,batch_id,severity,blocking,issue_scope,sheet_name,source_row_number,field_name,
                    issue_code,reason,raw_value,suggestion,sort_order)
                VALUES (:id,:batch,:severity,:blocking,:scope,:sheet,:row,:field,:code,:reason,:raw,:suggestion,:sort)
                """).param("id", UUID.randomUUID().toString()).param("batch", batch).param("severity", issue.severity())
                .param("blocking", issue.blocking()).param("scope", issue.scope()).param("sheet", issue.sheet())
                .param("row", issue.row()).param("field", issue.field()).param("code", issue.code())
                .param("reason", issue.reason()).param("raw", issue.rawValue()).param("suggestion", issue.suggestion())
                .param("sort", order).update();
    }
    private void insertPreview(String batch, PreviewInput row, int order) {
        try {
            jdbc.sql("INSERT INTO import_preview_row (id,batch_id,sheet_name,source_row_number,business_key,row_action,row_data_json,sort_order) VALUES (:id,:batch,:sheet,:row,:key,NULL,:json,:sort)")
                    .param("id", UUID.randomUUID().toString()).param("batch", batch).param("sheet", row.sheet())
                    .param("row", row.row()).param("key", row.businessKey()).param("json", mapper.writeValueAsString(row.data()))
                    .param("sort", order).update();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private Map<String,String> readMap(String json) { try { return mapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String select() { return "SELECT * FROM import_batch"; }
    private BatchRow map(ResultSet rs, int row) throws SQLException {
        return new BatchRow(rs.getString("id"), ImportType.valueOf(rs.getString("import_type")), rs.getString("template_version"),
                Status.valueOf(rs.getString("status")), Phase.valueOf(rs.getString("phase")), rs.getString("storage_key"),
                rs.getString("file_hash"), rs.getString("upload_payload_hash"), rs.getInt("total_rows"),
                rs.getInt("processed_rows"), rs.getInt("error_count"), rs.getInt("warning_count"),
                rs.getBoolean("commit_allowed"), rs.getString("preview_version"), instant(rs,"preview_expires_at"),
                rs.getString("revalidation_of_batch_id"), rs.getString("failure_code"),
                rs.getInt("result_created_rows"),rs.getInt("result_updated_rows"),rs.getInt("result_unchanged_rows"),
                rs.getString("result_storage_key"),instant(rs,"committed_at"),instant(rs,"created_at"), instant(rs,"updated_at"));
    }
    private Instant instant(ResultSet rs, String column) throws SQLException { var value=rs.getTimestamp(column); return value==null?null:value.toInstant(); }
    record OperationRow(String payloadHash, String responseBatchId) {}
    record BatchRow(String id, ImportType type, String version, Status status, Phase phase, String storageKey,
            String fileHash, String payloadHash, int totalRows, int processedRows, int errors, int warnings,
            boolean commitAllowed, String previewVersion, Instant previewExpiresAt, String source, String failureCode,
            int createdRows,int updatedRows,int unchangedRows,String resultStorageKey,Instant committedAt,
            Instant createdAt, Instant updatedAt) {
        BatchView view() { return new BatchView(id,type,version,status,phase,fileHash,totalRows,processedRows,
                totalRows==0?(status==Status.VALIDATED?100:0):processedRows*100/totalRows,errors,warnings,
                commitAllowed,previewVersion,previewExpiresAt,source,failureCode,createdAt,updatedAt); }
    }
    record ProductImportResult(int created,int updated,int unchanged){}
}
