package com.jiugeng.erp.importexport;

import java.time.Instant;
import java.util.List;
import java.util.Map;

final class ImportTypes {
    private ImportTypes() {}
    enum ImportType { PRODUCTS, VARIANTS, AUXILIARY_MATERIALS, PRODUCT_AUXILIARY_MATERIALS,
        AUXILIARY_MATERIAL_OPENING_INVENTORY }
    enum Status { QUEUED, VALIDATING, VALIDATED, COMMITTING, SUCCEEDED, FAILED, CANCELLED }
    enum Phase { WAITING, PARSING, VALIDATING_ROWS, BUILDING_PREVIEW, WAITING_CONFIRMATION,
        COMMITTING_ROWS, FINALIZING, DONE }
    record BatchView(String id, ImportType importType, String templateVersion, Status status, Phase phase,
            String fileHash, int totalRows, int processedRows, int percent, int errorCount, int warningCount,
            boolean commitAllowed, String previewVersion, Instant previewExpiresAt, String revalidationOfBatchId,
            String failureCode, Instant createdAt, Instant updatedAt) {}
    record IssueView(String id, String severity, boolean blocking, String scope, String sheet, Integer row,
            String field, String code, String reason, String rawValue, String suggestion) {}
    record PreviewRowView(String id, String sheet, int row, String businessKey, String action,
            Map<String, String> data) {}
    record PreviewView(String batchId, String previewVersion, Instant expiresAt, boolean commitAllowed,
            Map<String, Integer> summary, List<PreviewRowView> rows) {}
    record ValidationResult(int totalRows, List<IssueInput> issues, List<PreviewInput> rows) {}
    record IssueInput(String severity, boolean blocking, String scope, String sheet, Integer row, String field,
            String code, String reason, String rawValue, String suggestion) {}
    record PreviewInput(String sheet, int row, String businessKey, Map<String, String> data) {}
}
