package com.jiugeng.erp.purchase;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {PurchaseController.class,PurchaseManagementController.class})
class PurchaseExceptionHandler {
    @ExceptionHandler(com.jiugeng.erp.catalogmedia.CatalogImageGateway.AttachmentException.class)
    ResponseEntity<Map<String,Object>> image(com.jiugeng.erp.catalogmedia.CatalogImageGateway.AttachmentException e){return ResponseEntity.status(e.status()).body(Map.of("message",e.getMessage(),"code",e.code()));}
    @ExceptionHandler(com.jiugeng.erp.product.ProductPurchaseInventory.Failure.class)
    ResponseEntity<Map<String,Object>> stock(com.jiugeng.erp.product.ProductPurchaseInventory.Failure e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage(),"code",e.code));}
    @ExceptionHandler(com.jiugeng.erp.materialinventory.InventoryInboundGateway.Failure.class)
    ResponseEntity<Map<String,Object>> material(com.jiugeng.erp.materialinventory.InventoryInboundGateway.Failure e){return ResponseEntity.status(e.status).body(Map.of("message",e.getMessage(),"code",e.code));}
    @ExceptionHandler(PurchaseException.class)
    ResponseEntity<Map<String, Object>> handle(PurchaseException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "message", error.getMessage(),
                "timestamp", Instant.now().toString()));
    }
}
