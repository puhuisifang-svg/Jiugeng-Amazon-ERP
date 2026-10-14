package com.jiugeng.erp.materialinventory;

final class MaterialException extends RuntimeException {
    private final int status;
    private final String code;

    MaterialException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    int status() { return status; }
    String code() { return code; }
}
