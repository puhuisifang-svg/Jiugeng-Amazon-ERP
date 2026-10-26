package com.jiugeng.erp.customer;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(basePackages={"com.jiugeng.erp.customer","com.jiugeng.erp.dashboard"})
class CustomerExceptionHandler {
    // Avoid Spring's default validation logging of rejected customer contact values.
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
        org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Map<String,String>> invalidRequest(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("code","CUSTOMER_REQUEST_INVALID",
            "message","请检查必填项、字段长度及数值格式和范围"));
    }
    @ExceptionHandler(CustomerException.class)
    ResponseEntity<Map<String,String>> handle(CustomerException error) {
        return ResponseEntity.status(error.status).body(Map.of("code",error.code,"message",error.getMessage()));
    }
}
