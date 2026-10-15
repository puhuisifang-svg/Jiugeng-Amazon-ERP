package com.jiugeng.erp.catalogmedia;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CatalogImageController.class)
class CatalogImageExceptionHandler {
    @ExceptionHandler(CatalogImageException.class)
    ResponseEntity<Map<String,String>> handle(CatalogImageException exception){return ResponseEntity
            .status(exception.status()).body(Map.of("code",exception.code(),"message",exception.getMessage()));}
}
