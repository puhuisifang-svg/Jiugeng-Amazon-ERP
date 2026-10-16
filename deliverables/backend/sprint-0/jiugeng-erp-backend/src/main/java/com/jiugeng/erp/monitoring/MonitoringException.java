package com.jiugeng.erp.monitoring;

final class MonitoringException extends RuntimeException {
    final int status;
    MonitoringException(int status, String message) { super(message); this.status = status; }
}
