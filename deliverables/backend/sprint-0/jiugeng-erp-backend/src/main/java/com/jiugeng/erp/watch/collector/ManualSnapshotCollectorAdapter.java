package com.jiugeng.erp.watch.collector;
import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.time.Instant;import java.util.*;
/** Approved, non-network adapter for operator supplied ASIN snapshots. */
public final class ManualSnapshotCollectorAdapter implements CollectorAdapter{
 private static final java.util.regex.Pattern ASIN=java.util.regex.Pattern.compile("[A-Z0-9]{10,16}");
 @Override public String name(){return "manual-snapshot";} @Override public String version(){return "1.0";}
 @Override public CollectorResult collect(CollectorRequest request){throw new UnsupportedOperationException("Manual snapshot payload required");}
 @Override public CollectorResult collectManual(CollectorRequest request,ManualSnapshot input){
  new CollectorPolicyGuard().check(request);if(input.asins().size()>1000)throw new IllegalArgumentException("Snapshot cannot exceed 1000 rows");
  Set<String> asins=new LinkedHashSet<>();for(String raw:input.asins()){String value=raw==null?"":raw.trim().toUpperCase(Locale.ROOT);if(!ASIN.matcher(value).matches())throw new IllegalArgumentException("Invalid ASIN: "+raw);asins.add(value);}
  String canonical=String.join("\n",asins);String hash;try{hash=java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
  return new CollectorResult(new SnapshotManifest(request.runId(),request.storeKey(),request.marketplaceId(),input.status(),input.itemCount(),asins.size(),input.pageCount(),input.pagesComplete(),"manual://"+request.runId(),hash,Instant.now()),asins);
 }
}
