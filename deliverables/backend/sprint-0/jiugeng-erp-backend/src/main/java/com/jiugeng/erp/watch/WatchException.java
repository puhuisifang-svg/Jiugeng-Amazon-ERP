package com.jiugeng.erp.watch;
final class WatchException extends RuntimeException {private final int status;private final String code;WatchException(int status,String code,String message){super(message);this.status=status;this.code=code;}int status(){return status;}String code(){return code;}}
