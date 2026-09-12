package com.jiugeng.erp.watch.collector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

public final class DiffDeduplicator {

    public Set<DiffEvent> detectNewAsins(
            String storeKey,
            String marketplaceId,
            String runId,
            Instant detectedAt,
            Set<String> baselineAsins,
            Set<String> candidateAsins,
            Set<String> emittedEventKeys
    ) {
        return candidateAsins.stream()
                .filter(asin -> !baselineAsins.contains(asin))
                .map(asin -> new DiffEvent(eventKey(storeKey, marketplaceId, asin), storeKey,
                        marketplaceId, asin, runId, detectedAt))
                .filter(event -> !emittedEventKeys.contains(event.eventKey()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String eventKey(String storeKey, String marketplaceId, String asin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (storeKey + "|" + marketplaceId + "|" + asin).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }
}
