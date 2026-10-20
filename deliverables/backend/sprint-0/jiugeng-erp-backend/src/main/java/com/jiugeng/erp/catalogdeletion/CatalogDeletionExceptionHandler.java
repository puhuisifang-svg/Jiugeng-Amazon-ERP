package com.jiugeng.erp.catalogdeletion;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes=CatalogDeletionController.class)
class CatalogDeletionExceptionHandler {
    @ExceptionHandler(CatalogDeletionService.Failure.class)
    ResponseEntity<?> failure(CatalogDeletionService.Failure e) {
        return ResponseEntity.status(e.status).body(Map.of("code",e.code,"message",e.getMessage()));
    }
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<?> referenced(Exception e) { return ResponseEntity.status(409).body(Map.of("code","CATALOG_IN_USE","message","存在关联记录，不能删除；请刷新后检查业务引用")); }
    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    ResponseEntity<?> concurrent(Exception e) { return ResponseEntity.status(409).body(Map.of("code","CATALOG_BUSY","message","记录正在被其他操作使用，请刷新后重试")); }
    @ExceptionHandler({org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> invalid(Exception e) { return ResponseEntity.badRequest().body(Map.of("code","INVALID_REQUEST","message","请提供有效的记录版本")); }
}
