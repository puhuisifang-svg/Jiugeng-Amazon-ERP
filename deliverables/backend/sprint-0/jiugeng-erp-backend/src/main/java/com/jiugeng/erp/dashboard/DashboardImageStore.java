package com.jiugeng.erp.dashboard;

import java.io.IOException;

interface DashboardImageStore {
    StoredImage store(byte[] source) throws IOException;
    byte[] read(String key) throws IOException;
    void delete(String key);

    record StoredImage(String originalKey, String thumbnailKey, String storedMediaType, String sourceMediaType,
                       long sourceSize, String sha256, int width, int height) {}
}
