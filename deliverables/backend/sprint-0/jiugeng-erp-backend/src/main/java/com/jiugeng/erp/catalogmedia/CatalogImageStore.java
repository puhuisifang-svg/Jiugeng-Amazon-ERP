package com.jiugeng.erp.catalogmedia;

import java.io.IOException;

interface CatalogImageStore {
    StoredImage store(byte[] source) throws IOException;
    default StoredImage store(byte[] source,boolean optimizePhotographs)throws IOException{return store(source);}
    byte[] read(String key) throws IOException;
    default Variant thumbnail(String originalKey, boolean lossless) throws IOException { throw new IOException("Thumbnail generation unavailable"); }
    record Variant(String key, String mediaType, String etag) {}
    void delete(String key) throws IOException;
    record StoredImage(String originalKey,String thumbnailKey,String storedMediaType,String sourceMediaType,
                       long sourceSize,String sha256,int width,int height,byte[] thumbnailBytes){}
}
