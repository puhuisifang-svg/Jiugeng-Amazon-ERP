package com.jiugeng.erp.supplier;
final class SupplierException extends RuntimeException{private final int status;private final String code;SupplierException(int status,String code,String message){super(message);this.status=status;this.code=code;}int status(){return status;}String code(){return code;}}
