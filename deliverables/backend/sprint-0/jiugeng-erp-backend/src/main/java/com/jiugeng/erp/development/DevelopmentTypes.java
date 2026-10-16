package com.jiugeng.erp.development;

import com.jiugeng.erp.iam.AuditHistoryLookup;
import com.jiugeng.erp.product.ProductLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

final class DevelopmentTypes {
    private DevelopmentTypes() {}
    enum Status { DRAFT, IN_PROGRESS, COMPLETED, VOID }
    enum SourceType { CANDIDATE, MANUAL }
    record Fields(String name, String developer, String buyer, Integer stars, LocalDate startDate,
            LocalDate endDate, LocalDate launchDate, String productName, String brand, String category,
            String plannedSku, String material, BigDecimal purchaseCost, BigDecimal weight,
            BigDecimal length, BigDecimal width, BigDecimal height, String supplyNotes,
            String sampleNotes, String researchNotes, String remark, String linkedProductId) {}
    record Source(String candidateId, String asin, String title, String category, int score,
            BigDecimal referencePrice, String sourceUrl, String selectionReason) {}
    record View(String id, String number, Status status, long version, SourceType sourceType, Source source, Fields fields,
            String voidReason, Instant createdAt, Instant updatedAt) {}
    record Page(List<View> items, long total, int page, int size) {}
    record Detail(View item, List<AuditHistoryLookup.HistoryEvent> history, ProductLookup.ProductReference product) {}
    record Update(Long version, Fields fields) {}
    record Create(String requestId, Fields fields) {}
    record Transition(Long version, Status target, String reason) {}
}
