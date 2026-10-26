package com.jiugeng.erp.collection;

/** Implementations validate target state under a database lock and apply results in the scheduler transaction. */
public interface CollectionTarget {
    String kind();
    CollectionAdapter.Target prepare(String id);
    /** Return a concise change summary; empty means no notification. */
    String apply(CollectionAdapter.Target target,String provider,String jobId,CollectionAdapter.Observation observation);
}
