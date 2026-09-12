package com.jiugeng.erp.watch.collector;

public final class BaselineProtector {

    public BaselineDecision decide(String candidateRunId, String previousBaselineRunId, IntegrityResult integrity) {
        if (integrity.status() != IntegrityResult.Status.COMPLETE) {
            return new BaselineDecision(candidateRunId, previousBaselineRunId,
                    BaselineDecision.Decision.REJECTED_KEEP_PREVIOUS, "INTEGRITY_NOT_COMPLETE");
        }
        String reason = previousBaselineRunId == null ? "FIRST_COMPLETE_SNAPSHOT" : "COMPLETE_SUCCESSOR";
        return new BaselineDecision(candidateRunId, previousBaselineRunId,
                BaselineDecision.Decision.PROMOTED, reason);
    }
}
