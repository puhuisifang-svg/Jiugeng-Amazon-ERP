package com.jiugeng.erp.catalogmedia;

final class CatalogImageException extends RuntimeException {
    private final int status;
    private final String code;
    CatalogImageException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    int status(){return status;} String code(){return code;}
}
