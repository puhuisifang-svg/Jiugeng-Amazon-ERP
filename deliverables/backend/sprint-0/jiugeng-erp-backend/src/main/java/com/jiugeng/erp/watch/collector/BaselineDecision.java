package com.jiugeng.erp.watch.collector;

public record BaselineDecision(
        String candidateRunId,
        String previousBaselineRunId,
        Decision decision,
        String reasonCode
) {
    public enum Decision { PROMOTED, REJECTED_KEEP_PREVIOUS }
}
