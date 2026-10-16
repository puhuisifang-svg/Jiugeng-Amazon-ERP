package com.jiugeng.erp.selection;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.math.BigDecimal;

/** Public approval snapshot. Synchronous consumers join the selection transaction. */
public record CandidateApproved(String candidateId, String asin, String title, String category,
        Integer score, BigDecimal referencePrice, String sourceUrl, String selectionReason,
        String owner, AuthenticatedActor actor) {}
