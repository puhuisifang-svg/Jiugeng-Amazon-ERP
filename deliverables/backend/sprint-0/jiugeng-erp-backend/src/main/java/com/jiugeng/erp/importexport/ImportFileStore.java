package com.jiugeng.erp.importexport;

import java.io.IOException;
import java.io.InputStream;

interface ImportFileStore {
    String store(InputStream input) throws IOException;
    String storeResult(InputStream input) throws IOException;
    InputStream read(String key) throws IOException;
    void delete(String key);
}
