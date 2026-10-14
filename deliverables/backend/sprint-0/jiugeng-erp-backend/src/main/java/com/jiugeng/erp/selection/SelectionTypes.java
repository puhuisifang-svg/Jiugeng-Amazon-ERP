package com.jiugeng.erp.selection;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

final class SelectionTypes {
    private SelectionTypes() {}

    enum CandidateStatus { NEW, REVIEWING, APPROVED, REJECTED }
    enum CandidateSource { MANUAL, WATCH }

    record CandidateView(String id, String asin, String title, CandidateStatus status, Integer score,
            String rejectionReason, String sourceUrl, String category, BigDecimal price, BigDecimal rating,
            Integer reviewCount, String owner, List<String> tags, String selectionReason, CandidateSource source,
            String sourceStore, Instant discoveredAt, String sourceRunId, String sourceEventId) {}

    record NoteView(String id, String content, String authorId, String authorName, String authorRole,
            Instant createdAt) {}
    record EvidenceView(String id, String name, String url, String description, String addedById,
            String addedByName, String addedByRole, Instant createdAt) {}
    record DetailView(CandidateView candidate, List<NoteView> notes, List<EvidenceView> evidence) {}
    record EvaluationInput(String category, BigDecimal price, BigDecimal rating, Integer reviewCount,
            String owner, List<String> tags, String selectionReason) {}
}
