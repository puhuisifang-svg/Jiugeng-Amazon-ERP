package com.jiugeng.erp.watch;

import java.time.Instant;
import java.util.List;

final class WatchTypes {
    private WatchTypes(){}
    enum RunStatus { SUCCEEDED, FAILED }
    record StoreView(String id,String storeKey,String storeName,String sourceUrl,String status,String baselineRunId,
            int baselineAsinCount,String lastRunId,String lastRunStatus,String lastIntegrityStatus,String lastDecision,
            String lastReasonCode,Instant lastRunAt,long version,String owner,String remark,Instant createdAt){}
    record EventView(String id,String storeId,String storeName,String asin,String title,Instant detectedAt,
            String sourceRunId,String status,String candidateId,long version,String ignoreReason){}
    record RunView(String id,String storeId,String requestId,String status,String integrityStatus,String decision,
            String reasonCode,int itemCount,int uniqueAsinCount,int pageCount,boolean pagesComplete,Instant completedAt){}
    record RunResult(StoreView item,List<EventView> events,boolean baselineChanged,RunView run,boolean replayed){}
    record Confirmation(EventView item,CandidateRef candidate,boolean replayed){}
    record CandidateRef(String id){}
    record StoredRun(String id,String storeId,String payloadHash,String decision){}
}
