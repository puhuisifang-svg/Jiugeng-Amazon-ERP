package com.jiugeng.erp.purchase;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PurchaseController.class)
class PurchaseExceptionHandler {
    @ExceptionHandler(PurchaseException.class)
    ResponseEntity<Map<String, Object>> handle(PurchaseException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "message", error.getMessage(),
                "timestamp", Instant.now().toString()));
    }
}
