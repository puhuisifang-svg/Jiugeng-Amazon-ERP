package com.jiugeng.erp.collection;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Set;

/** Only server-installed, authorized adapters may implement this port. No user supplied URLs or credentials. */
public interface CollectionAdapter {
    String provider();
    Set<String> kinds();
    boolean approved();
    /** Must enforce approved host, quota, concurrency, timeout and pagination contract before reporting COMPLETE. */
    Observation collect(Target target) throws CollectionFailure;
    record Target(String kind,String id,String key,long version) {}
    record Observation(Instant observedAt,String quality,boolean pagesComplete,JsonNode fields) {}
    final class CollectionFailure extends RuntimeException {
        private final boolean retryable; private final long retryAfterSeconds;
        public CollectionFailure(boolean retryable,long retryAfterSeconds){super("数据源调用失败");this.retryable=retryable;this.retryAfterSeconds=retryAfterSeconds;}
        public boolean retryable(){return retryable;} public long retryAfterSeconds(){return retryAfterSeconds;}
    }
}
