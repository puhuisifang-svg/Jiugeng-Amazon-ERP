package com.jiugeng.erp.product;

final class ProductException extends RuntimeException {
    private final int status;
    private final String code;

    ProductException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    int status() { return status; }
    String code() { return code; }
}
