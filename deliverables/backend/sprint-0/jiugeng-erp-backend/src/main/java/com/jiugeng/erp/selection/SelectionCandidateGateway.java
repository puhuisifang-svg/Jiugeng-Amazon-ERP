package com.jiugeng.erp.selection;

import com.jiugeng.erp.iam.AuthenticatedActor;
import java.time.Instant;

/** Stable selection boundary used by the watch module to confirm a traceable candidate. */
public interface SelectionCandidateGateway {
    Confirmation confirmWatchCandidate(String asin, String title, String storeName, Instant discoveredAt,
            String runId, String eventId, AuthenticatedActor actor);
    record Confirmation(String candidateId, boolean reused) {}
}
