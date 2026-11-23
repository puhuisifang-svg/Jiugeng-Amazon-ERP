package com.jiugeng.erp.catalogmedia;

import java.util.List;
/** Each domain exposes only image URLs the caller can already read in that domain. */
public interface ExternalImageSource {
    String permission();
    boolean contains(String url);
    List<String> page(String after,int limit);
    record Batch(List<String> urls,String cursor){}
    default Batch scan(String after,int limit){var urls=page(after,limit);return new Batch(urls,urls.isEmpty()?"":urls.get(urls.size()-1));}
    default boolean lossless(){return false;}
}
