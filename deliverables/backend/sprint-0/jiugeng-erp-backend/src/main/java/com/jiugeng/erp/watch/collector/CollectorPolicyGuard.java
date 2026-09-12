package com.jiugeng.erp.watch.collector;

public final class CollectorPolicyGuard {

    public void check(CollectorRequest request) {
        if (request.approval().status() != SourceApproval.Status.APPROVED) {
            throw new PolicyViolationException("Collector source is not approved");
        }
        if (request.approval().requiresBypass()) {
            throw new PolicyViolationException("Bypassing access controls is prohibited");
        }
        if (!"ATVPDKIKX0DER".equals(request.marketplaceId())) {
            throw new PolicyViolationException("Sprint 0 scope is Amazon US only");
        }
    }

    public static final class PolicyViolationException extends IllegalStateException {
        public PolicyViolationException(String message) {
            super(message);
        }
    }
}
