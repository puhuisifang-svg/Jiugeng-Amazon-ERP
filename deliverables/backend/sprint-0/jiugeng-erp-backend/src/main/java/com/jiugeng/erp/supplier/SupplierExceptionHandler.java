package com.jiugeng.erp.supplier;
import java.util.Map;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.ExceptionHandler;import org.springframework.web.bind.annotation.RestControllerAdvice;
@RestControllerAdvice(assignableTypes=SupplierController.class) class SupplierExceptionHandler{@ExceptionHandler(SupplierException.class)ResponseEntity<Map<String,String>> handle(SupplierException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}}
