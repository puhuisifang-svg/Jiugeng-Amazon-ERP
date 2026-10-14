package com.jiugeng.erp.iam;

final class IamException extends RuntimeException {
    private final int status;
    private final String code;

    IamException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static IamException unauthorized() {
        return new IamException(401, "AUTHENTICATION_REQUIRED", "登录已失效，请重新登录");
    }

    int status() {
        return status;
    }

    String code() {
        return code;
    }
}
