package com.jiugeng.erp.catalogmedia;

import java.io.IOException;

interface CatalogImageStore {
    StoredImage store(byte[] source) throws IOException;
    byte[] read(String key) throws IOException;
    void delete(String key) throws IOException;
    record StoredImage(String originalKey,String thumbnailKey,String storedMediaType,String sourceMediaType,
                       long sourceSize,String sha256,int width,int height){}
}
