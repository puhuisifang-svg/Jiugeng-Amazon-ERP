package com.jiugeng.erp.watch.collector;

import java.util.List;
import java.util.Set;

public final class SnapshotIntegrityEvaluator {

    public IntegrityResult evaluate(SnapshotManifest manifest, Set<String> asins) {
        boolean sourceSucceeded = manifest.status() == SnapshotManifest.RunStatus.SUCCEEDED;
        List<IntegrityResult.Check> checks = List.of(
                check("SOURCE_SUCCEEDED", sourceSucceeded, manifest.status().name()),
                check("PAGES_COMPLETE", manifest.pagesComplete(), Boolean.toString(manifest.pagesComplete())),
                check("COUNT_MATCH", manifest.itemCount() == asins.size(), manifest.itemCount() + "/" + asins.size()),
                check("ASIN_UNIQUE", manifest.uniqueAsinCount() == asins.size(), manifest.uniqueAsinCount() + "/" + asins.size()),
                check("EVIDENCE_HASH_PRESENT", manifest.contentSha256().matches("[a-f0-9]{64}"), "sha256")
        );

        if (!sourceSucceeded) {
            return new IntegrityResult(IntegrityResult.Status.FAILED, checks);
        }
        boolean complete = checks.stream().allMatch(IntegrityResult.Check::passed);
        return new IntegrityResult(complete ? IntegrityResult.Status.COMPLETE : IntegrityResult.Status.INCOMPLETE, checks);
    }

    private IntegrityResult.Check check(String code, boolean passed, String actual) {
        return new IntegrityResult.Check(code, passed, actual);
    }
}
