package com.jiugeng.erp.selection;

final class SelectionException extends RuntimeException {
    private final int status;
    private final String code;
    SelectionException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    int status() { return status; }
    String code() { return code; }
}
