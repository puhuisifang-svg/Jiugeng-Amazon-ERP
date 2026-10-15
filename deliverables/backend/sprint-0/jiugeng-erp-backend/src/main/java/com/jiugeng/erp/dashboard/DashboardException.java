package com.jiugeng.erp.dashboard;

final class DashboardException extends RuntimeException {
    private final int status;
    private final String code;

    DashboardException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    int status() { return status; }
    String code() { return code; }
}
