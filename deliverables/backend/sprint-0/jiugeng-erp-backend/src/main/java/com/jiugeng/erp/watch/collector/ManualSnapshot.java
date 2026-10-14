package com.jiugeng.erp.watch.collector;
import java.util.List;
public record ManualSnapshot(SnapshotManifest.RunStatus status,int itemCount,int pageCount,boolean pagesComplete,List<String> asins){public ManualSnapshot{asins=List.copyOf(asins);}}
