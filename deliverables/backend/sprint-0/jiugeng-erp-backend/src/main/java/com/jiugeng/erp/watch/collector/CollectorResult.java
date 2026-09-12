package com.jiugeng.erp.watch.collector;

import java.util.Objects;
import java.util.Set;

public record CollectorResult(SnapshotManifest manifest, Set<String> asins) {
    public CollectorResult {
        Objects.requireNonNull(manifest, "manifest");
        asins = Set.copyOf(asins);
    }
}
