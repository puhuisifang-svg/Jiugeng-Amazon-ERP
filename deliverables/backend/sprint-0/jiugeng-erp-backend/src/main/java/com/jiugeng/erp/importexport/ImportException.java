package com.jiugeng.erp.importexport;

final class ImportException extends RuntimeException {
    private final int status;
    private final String code;
    ImportException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    int status() { return status; }
    String code() { return code; }
}
