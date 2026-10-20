package com.jiugeng.erp.customer;

class CustomerException extends RuntimeException {
    final int status;
    final String code;
    CustomerException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
}
