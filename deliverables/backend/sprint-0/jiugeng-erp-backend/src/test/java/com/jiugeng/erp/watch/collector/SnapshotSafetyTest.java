package com.jiugeng.erp.watch.collector;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotSafetyTest {

    private static final String MARKETPLACE = "ATVPDKIKX0DER";
    private static final String HASH = "a".repeat(64);

    @Test
    void incompleteSnapshotCannotReplaceBaseline() {
        SnapshotManifest manifest = manifest("run-bad", 2, 1, false);
        IntegrityResult integrity = new SnapshotIntegrityEvaluator().evaluate(manifest, Set.of("B000000001"));
        BaselineDecision decision = new BaselineProtector().decide("run-bad", "run-good", integrity);

        assertEquals(IntegrityResult.Status.INCOMPLETE, integrity.status());
        assertEquals(BaselineDecision.Decision.REJECTED_KEEP_PREVIOUS, decision.decision());
        assertEquals("run-good", decision.previousBaselineRunId());
    }

    @Test
    void completeSnapshotCanBecomeBaseline() {
        SnapshotManifest manifest = manifest("run-good", 2, 2, true);
        IntegrityResult integrity = new SnapshotIntegrityEvaluator().evaluate(
                manifest, Set.of("B000000001", "B000000002"));

        assertEquals(IntegrityResult.Status.COMPLETE, integrity.status());
        assertEquals(BaselineDecision.Decision.PROMOTED,
                new BaselineProtector().decide("run-good", null, integrity).decision());
    }

    @Test
    void rerunDoesNotEmitDuplicateNewAsinEvent() {
        DiffDeduplicator deduplicator = new DiffDeduplicator();
        Set<DiffEvent> first = deduplicator.detectNewAsins("STORE_A", MARKETPLACE, "run-2",
                Instant.parse("2026-09-12T00:00:00Z"), Set.of("B000000001"),
                Set.of("B000000001", "B000000002"), Set.of());
        Set<String> emittedKeys = Set.of(first.iterator().next().eventKey());
        Set<DiffEvent> rerun = deduplicator.detectNewAsins("STORE_A", MARKETPLACE, "run-2-retry",
                Instant.parse("2026-09-12T00:05:00Z"), Set.of("B000000001"),
                Set.of("B000000001", "B000000002"), emittedKeys);

        assertEquals(1, first.size());
        assertTrue(rerun.isEmpty());
    }

    private SnapshotManifest manifest(String runId, int itemCount, int uniqueCount, boolean pagesComplete) {
        return new SnapshotManifest(runId, "STORE_A", MARKETPLACE,
                SnapshotManifest.RunStatus.SUCCEEDED, itemCount, uniqueCount, 1,
                pagesComplete, "s3://evidence/run", HASH, Instant.parse("2026-09-12T00:00:00Z"));
    }
}
