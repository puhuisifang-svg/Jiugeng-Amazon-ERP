package com.jiugeng.erp.watch.collector;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectorPolicyGuardTest {

    private final CollectorPolicyGuard guard = new CollectorPolicyGuard();

    @Test
    void permitsApprovedSourceThatNeedsNoBypass() {
        assertDoesNotThrow(() -> guard.check(request(SourceApproval.Status.APPROVED, false)));
    }

    @Test
    void rejectsUnapprovedOrBypassDependentSources() {
        assertThrows(CollectorPolicyGuard.PolicyViolationException.class,
                () -> guard.check(request(SourceApproval.Status.PENDING, false)));
        assertThrows(CollectorPolicyGuard.PolicyViolationException.class,
                () -> guard.check(request(SourceApproval.Status.APPROVED, true)));
    }

    private CollectorRequest request(SourceApproval.Status status, boolean requiresBypass) {
        return new CollectorRequest("run-1", "STORE_A", "ATVPDKIKX0DER",
                new SourceApproval(status, SourceApproval.SourceClass.APPROVED_API,
                        requiresBypass, Instant.parse("2026-09-12T00:00:00Z")));
    }
}
