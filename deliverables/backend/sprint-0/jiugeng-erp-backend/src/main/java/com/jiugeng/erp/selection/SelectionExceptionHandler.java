package com.jiugeng.erp.selection;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SelectionController.class)
@Profile("!mock")
class SelectionExceptionHandler {
    @ExceptionHandler(SelectionException.class)
    ResponseEntity<Problem> business(SelectionException exception) {
        return ResponseEntity.status(exception.status()).body(new Problem(exception.code(), exception.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Problem> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(new Problem("INVALID_REQUEST", "请求参数不符合要求"));
    }
    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<Problem> forbidden(AuthorizationDeniedException ignored) {
        return ResponseEntity.status(403).body(new Problem("CAPABILITY_DENIED", "当前账号无权执行此操作"));
    }
    record Problem(String code, String detail) {}
}
